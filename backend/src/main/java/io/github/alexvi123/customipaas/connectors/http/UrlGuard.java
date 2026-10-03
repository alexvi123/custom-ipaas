package io.github.alexvi123.customipaas.connectors.http;

import io.github.alexvi123.customipaas.connector.ConnectorException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF protection: stops the HTTP connector from being used to reach this machine, the private network or
 * cloud metadata endpoints (e.g. 169.254.169.254). Known limitation: DNS could change between this check
 * and the actual connection (DNS rebinding).
 */
public class UrlGuard {

	private final boolean allowPrivateNetworks;

	public UrlGuard(boolean allowPrivateNetworks) {
		this.allowPrivateNetworks = allowPrivateNetworks;
	}

	public void check(URI uri) {
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			throw new ConnectorException("Only http and https URLs are allowed");
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new ConnectorException("URL has no host");
		}
		if (allowPrivateNetworks) {
			return;
		}

		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		} catch (UnknownHostException e) {
			throw new ConnectorException("Unknown host '" + host + "'");
		}
		for (InetAddress address : addresses) {
			if (isInternal(address)) {
				throw new ConnectorException("Blocked: '" + host + "' points to a private or internal address ("
						+ address.getHostAddress() + ")");
			}
		}
	}

	static boolean isInternal(InetAddress address) {
		if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		byte[] bytes = address.getAddress();
		if (address instanceof Inet6Address) {
			return (bytes[0] & 0xfe) == 0xfc; // fc00::/7 unique local
		}
		int first = bytes[0] & 0xff;
		int second = bytes[1] & 0xff;
		return first == 0 || (first == 100 && (second & 0xc0) == 64); // 0.0.0.0/8, 100.64.0.0/10
	}
}
