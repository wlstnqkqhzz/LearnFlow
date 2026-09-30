package com.be.push;

import com.be.global.exception.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** Fixed provider allowlist; no user-supplied hosts, ports, proxy or redirects. */
@Component
public class PushEndpointPolicy {
    public URI validateEndpoint(String endpoint) {
        try {
            if (endpoint == null || endpoint.length() > 2048) throw new IllegalArgumentException();
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || !allowedHost(host)
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || !allowedQuery(host, uri)
                    || uri.getRawPath() == null || uri.getRawPath().length() < 2)
                throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException e) { throw invalid(); }
    }
    static boolean allowedHost(String host) {
        return host.equals("fcm.googleapis.com") || host.equals("web.push.apple.com")
                || host.equals("updates.push.services.mozilla.com")
                || host.matches("[a-z0-9-]+\\.notify\\.windows\\.com");
    }
    private boolean allowedQuery(String host, URI uri) {
        // Edge/WNS subscriptions carry an opaque token in the query string.
        return uri.getRawQuery() == null || (host.endsWith(".notify.windows.com")
                && "/w/".equals(uri.getRawPath()) && uri.getRawQuery().matches("token=[A-Za-z0-9_%+/=-]+"));
    }
    public void validate(PushSubscriptionRequest request, Instant now) {
        validateEndpoint(request.endpoint());
        try {
            byte[] key = decode(request.p256dh(), 65);
            decode(request.auth(), 16);
            if (key[0] != 4 || (request.expirationTime() != null && !request.expirationTime().isAfter(now)))
                throw new IllegalArgumentException();
            var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            var spec = parameters.getParameterSpec(ECParameterSpec.class);
            var x = new BigInteger(1, Arrays.copyOfRange(key, 1, 33));
            var y = new BigInteger(1, Arrays.copyOfRange(key, 33, 65));
            var prime = ((ECFieldFp) spec.getCurve().getField()).getP();
            if (x.compareTo(prime) >= 0 || y.compareTo(prime) >= 0
                    || !y.multiply(y).mod(prime).equals(x.pow(3).add(spec.getCurve().getA().multiply(x)).add(spec.getCurve().getB()).mod(prime)))
                throw new IllegalArgumentException();
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw invalid(); }
    }
    private byte[] decode(String value, int length) {
        if (value == null || !value.matches("[A-Za-z0-9_-]+={0,2}")) throw new IllegalArgumentException();
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        if (bytes.length != length) throw new IllegalArgumentException();
        return bytes;
    }
    /** Applied by the actual socket DNS resolver, not just a preliminary DNS lookup. */
    public InetAddress[] resolve(String host) throws UnknownHostException {
        if (!allowedHost(host)) throw new UnknownHostException("Push host rejected");
        return requirePublic(InetAddress.getAllByName(host));
    }
    static InetAddress[] requirePublic(InetAddress[] addresses) throws UnknownHostException {
        if (addresses.length == 0) throw new UnknownHostException("Push DNS rejected");
        for (var address : addresses) {
            byte[] bytes = address.getAddress();
            boolean global;
            if (bytes.length == 4) {
                int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
                global = a != 0 && a != 10 && a != 127 && a < 224
                        && !(a == 100 && b >= 64 && b <= 127) && !(a == 169 && b == 254)
                        && !(a == 172 && b >= 16 && b <= 31) && !(a == 192 && b == 168)
                        && !(a == 192 && b == 0) && !(a == 192 && b == 2)
                        && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                        && !(a == 203 && b == 0 && c == 113);
            } else {
                // Only global unicast; exclude documentation and transition/special ranges.
                global = (bytes[0] & 0xe0) == 0x20
                        && !((bytes[0] & 255) == 0x20 && (bytes[1] & 255) == 0x02)
                        && !((bytes[0] & 255) == 0x20 && (bytes[1] & 255) == 0x01
                        && (((bytes[2] & 255) < 2) || ((bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8)));
            }
            if (!global || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress())
                throw new UnknownHostException("Push DNS rejected");
        }
        return addresses;
    }
    private BusinessException invalid() { return new BusinessException(ErrorCode.INVALID_PUSH_SUBSCRIPTION); }
}
