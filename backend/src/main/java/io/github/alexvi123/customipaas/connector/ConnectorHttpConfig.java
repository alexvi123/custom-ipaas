package io.github.alexvi123.customipaas.connector;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The HTTP client connectors use to call external apps. */
@Configuration
public class ConnectorHttpConfig {

	@Bean
	public RestClient connectorRestClient() {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER) // a redirect could point to an internal address
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(15));
		return RestClient.builder().requestFactory(requestFactory).build();
	}
}
