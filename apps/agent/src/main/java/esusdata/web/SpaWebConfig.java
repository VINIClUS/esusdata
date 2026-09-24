package esusdata.web;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the web client (apps/web, bundled under {@code static/} by {@code mvn -Pweb}) from the same
 * origin as the API. The client routes with the history API, so a deep link like
 * {@code /indicadores/c1-mais-acesso} has no file behind it and gets {@code index.html}. Without
 * {@code -Pweb} there is no {@code index.html} and every such path is a plain 404.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String LOCATION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite puts a content hash in every asset name, so a cached copy can never go stale.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(LOCATION + "assets/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        // index.html (and anything unhashed) is revalidated, so a new build is picked up at once.
        registry.addResourceHandler("/**")
                .addResourceLocations(LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            // super keeps PathResourceResolver's check that the resource stays under the location.
            Resource resource = super.getResource(resourcePath, location);
            if (resource != null || !isClientRoute(resourcePath)) {
                return resource;
            }
            Resource index = location.createRelative("index.html");
            return index.isReadable() ? index : null;
        }

        /** Unknown API paths and missing files (anything with an extension) stay 404. */
        private static boolean isClientRoute(String resourcePath) {
            if ("api".equals(resourcePath) || resourcePath.startsWith("api/")) {
                return false;
            }
            String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            return !lastSegment.contains(".");
        }
    }
}
