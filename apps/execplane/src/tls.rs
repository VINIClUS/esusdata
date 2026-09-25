use postgres::config::SslMode;
use postgres::{CancelToken, Client, Config, NoTls};
use rustls::pki_types::pem::PemObject;
use rustls::pki_types::CertificateDer;
use rustls::{ClientConfig, RootCertStore};
use std::error::Error;
use std::sync::Arc;
use tokio_postgres_rustls::MakeRustlsConnect;

/// How this process reaches the source (ADR 0022). Java decides, per deployment: with
/// `observatorio.source.tls-root-cert` set, every session is TLS-only (`sslmode=require`) and
/// the server must present a chain to one of those roots for the exact host or IP the envelope
/// names — Tech Spec §1.12.6's "transporte cifrado com validação de certificado" for a source
/// reached over a network. Without it the session is plaintext, which Java only allows for
/// loopback destinations (the SSH tunnel of ADR 0003).
#[derive(Clone)]
pub enum SessionTls {
    Plain,
    Verified(MakeRustlsConnect),
}

impl SessionTls {
    /// `root_cert` is the PEM path from the envelope's `tls_root_cert`. An unreadable file, or
    /// one with no certificate in it, is an error: falling back to plaintext would silently drop
    /// the protection the deployment asked for.
    pub fn from_root_cert(root_cert: Option<&str>) -> Result<Self, Box<dyn Error>> {
        let Some(path) = root_cert else {
            return Ok(Self::Plain);
        };
        let mut roots = RootCertStore::empty();
        for certificate in CertificateDer::pem_file_iter(path)? {
            roots.add(certificate?)?;
        }
        if roots.is_empty() {
            return Err(format!("no certificate found in tls_root_cert {path}").into());
        }
        let config =
            ClientConfig::builder_with_provider(Arc::new(rustls::crypto::ring::default_provider()))
                .with_safe_default_protocol_versions()?
                .with_root_certificates(roots)
                .with_no_client_auth();
        Ok(Self::Verified(MakeRustlsConnect::new(config)))
    }

    pub fn connect(&self, config: &mut Config) -> Result<Client, postgres::Error> {
        match self {
            Self::Plain => config.connect(NoTls),
            Self::Verified(tls) => config.ssl_mode(SslMode::Require).connect(tls.clone()),
        }
    }
}

/// PostgreSQL's cancel signal travels on a new connection, so it must be opened the same way as
/// the session it cancels — over TLS when the session was.
#[derive(Clone)]
pub struct Canceller {
    token: CancelToken,
    tls: SessionTls,
}

impl Canceller {
    pub fn new(token: CancelToken, tls: SessionTls) -> Self {
        Self { token, tls }
    }

    /// Best effort, like `Statement.cancel()`: a failed cancel leaves the budget and the
    /// connection's own timeouts to end the query.
    pub fn cancel(&self) {
        let _ = match &self.tls {
            SessionTls::Plain => self.token.cancel_query(NoTls),
            SessionTls::Verified(tls) => self.token.cancel_query(tls.clone()),
        };
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    /// A self-signed certificate is enough here: only parsing and the root store are exercised.
    const CERTIFICATE: &str = "-----BEGIN CERTIFICATE-----
MIIBjjCCATOgAwIBAgIUD59RKx+nkCDOiOQzgRDNWg2JH2QwCgYIKoZIzj0EAwIw
HDEaMBgGA1UEAwwRZXhlY3BsYW5lLXRlc3QtY2EwHhcNMjYwOTI1MTgzNDI5WhcN
MzYwOTIyMTgzNDI5WjAcMRowGAYDVQQDDBFleGVjcGxhbmUtdGVzdC1jYTBZMBMG
ByqGSM49AgEGCCqGSM49AwEHA0IABNpOxJ3yVFjI9GV62aYfRIzWM+aAvxhNNO6H
TPWqxXLuQafYvNyFHph/7CYf3SpFyQFTO7uGCYlBQrohuqk/qdqjUzBRMB0GA1Ud
DgQWBBSLxKoqfzrUNFHp8xMaCClIdtLeUTAfBgNVHSMEGDAWgBSLxKoqfzrUNFHp
8xMaCClIdtLeUTAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0kAMEYCIQDb
N4gGv8//vn7kWcHuL4GWcNyyPDVCAoF8WtuilNdVaAIhAMcQsCujs4S2jVfJjJhf
ctfHPFPKLIyuncUVDk1Luih4
-----END CERTIFICATE-----
";

    /// One file per call: the tests run in parallel threads of one process, so the PID alone
    /// would hand two of them the same path.
    fn file_with(content: &str) -> std::path::PathBuf {
        static NEXT: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(0);
        let path = std::env::temp_dir().join(format!(
            "execplane-tls-{}-{}.pem",
            std::process::id(),
            NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
        ));
        std::fs::File::create(&path)
            .unwrap()
            .write_all(content.as_bytes())
            .unwrap();
        path
    }

    #[test]
    fn no_root_certificate_means_a_plaintext_session() {
        assert!(matches!(
            SessionTls::from_root_cert(None).unwrap(),
            SessionTls::Plain
        ));
    }

    #[test]
    fn a_root_certificate_means_a_verified_tls_session() {
        let path = file_with(CERTIFICATE);
        let tls = SessionTls::from_root_cert(path.to_str()).unwrap();
        std::fs::remove_file(&path).unwrap();
        assert!(matches!(tls, SessionTls::Verified(_)));
    }

    /// A port nothing listens on: both modes must surface the refused connection as an error.
    fn unreachable() -> Config {
        let mut config = Config::new();
        config
            .host("127.0.0.1")
            .port(1)
            .user("u")
            .dbname("d")
            .connect_timeout(std::time::Duration::from_secs(2));
        config
    }

    #[test]
    fn either_mode_reports_an_unreachable_server_as_an_error() {
        assert!(SessionTls::Plain.connect(&mut unreachable()).is_err());
        let path = file_with(CERTIFICATE);
        let verified = SessionTls::from_root_cert(path.to_str()).unwrap();
        std::fs::remove_file(&path).unwrap();
        let mut config = unreachable();
        assert!(verified.connect(&mut config).is_err());
        assert_eq!(config.get_ssl_mode(), SslMode::Require);
    }

    #[test]
    fn a_missing_root_certificate_file_is_an_error_not_plaintext() {
        assert!(SessionTls::from_root_cert(Some("/nonexistent/execplane-root.pem")).is_err());
    }

    #[test]
    fn a_root_certificate_file_without_certificates_is_an_error_not_plaintext() {
        let path = file_with("not a certificate\n");
        let result = SessionTls::from_root_cert(path.to_str());
        std::fs::remove_file(&path).unwrap();
        assert!(result.is_err());
    }
}
