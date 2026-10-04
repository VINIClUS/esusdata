//! A throwaway PostgreSQL 9.6.13 — the PEC's version — in Docker, for the execution plane's own
//! tests: `canonical.rs`'s typed read (included there with `#[path]`) and the binary's v2
//! handshake (`tests/canonical_protocol.rs`). `start` returns `None` where Docker cannot run a
//! Linux container (the Windows runner); the caller then skips, like the Java tests tagged
//! `docker`.

use std::process::Command;
use std::time::{Duration, Instant};

pub const USER: &str = "execplane";
pub const PASSWORD: &str = "execplane-test";
pub const DATABASE: &str = "esus_test";
const IMAGE: &str = "postgres:9.6.13";

pub struct Postgres {
    id: String,
    pub port: u16,
}

impl Postgres {
    /// Starts the container, published on a free loopback port, and waits until it accepts a
    /// session — the image's entrypoint restarts the server once after `initdb`.
    pub fn start() -> Option<Self> {
        let started = Command::new("docker")
            .args(["run", "-d", "--rm", "-p", "127.0.0.1::5432"])
            .args(["-e", &format!("POSTGRES_USER={USER}")])
            .args(["-e", &format!("POSTGRES_PASSWORD={PASSWORD}")])
            .args(["-e", &format!("POSTGRES_DB={DATABASE}")])
            .arg(IMAGE)
            .output()
            .ok()?;
        if !started.status.success() {
            eprintln!(
                "skipping: Docker cannot start {IMAGE}: {}",
                String::from_utf8_lossy(&started.stderr).trim()
            );
            return None;
        }
        // From here on, dropping `container` removes it, whatever fails next.
        let mut container = Self {
            id: String::from_utf8_lossy(&started.stdout).trim().to_string(),
            port: 0,
        };
        let published = Command::new("docker")
            .args(["port", &container.id, "5432/tcp"])
            .output()
            .ok()?;
        container.port = String::from_utf8_lossy(&published.stdout)
            .lines()
            .find_map(|line| line.rsplit(':').next()?.trim().parse().ok())?;

        let deadline = Instant::now() + Duration::from_secs(90);
        while Instant::now() < deadline {
            if let Ok(mut client) = container.connect() {
                if client.simple_query("SELECT 1").is_ok() {
                    return Some(container);
                }
            }
            std::thread::sleep(Duration::from_millis(250));
        }
        eprintln!("skipping: {IMAGE} did not accept sessions within 90 s");
        None
    }

    /// A plaintext session with the container's credentials.
    pub fn connect(&self) -> Result<postgres::Client, postgres::Error> {
        postgres::Config::new()
            .host("127.0.0.1")
            .port(self.port)
            .user(USER)
            .password(PASSWORD)
            .dbname(DATABASE)
            .connect_timeout(Duration::from_secs(5))
            .connect(postgres::NoTls)
    }
}

impl Drop for Postgres {
    fn drop(&mut self) {
        let _ = Command::new("docker").args(["rm", "-f", &self.id]).output();
    }
}
