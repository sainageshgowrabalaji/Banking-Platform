package com.sainagesh.bank.testing;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One of the OpenAPI files in {@code contracts/openapi}, used to check what a service really sends.
 *
 * <p>The contract is written first and other teams build against it. This class is what keeps the
 * code honest. Every response a test receives is compared with the contract, so a field that is
 * renamed, a status code nobody wrote down, or an endpoint missing from the file fails the build.
 *
 * <p>Responses are always checked. A request is checked only when the service accepted it, because
 * many tests send a broken request on purpose to see it refused.
 */
public final class Contract {

    private final String file;
    private final OpenApiInteractionValidator validator;

    private Contract(String file, OpenApiInteractionValidator validator) {
        this.file = file;
        this.validator = validator;
    }

    /**
     * Loads a contract by file name, such as {@code ledger-v1.yaml}. The {@code contracts} folder is
     * found by walking up from the folder the test runs in.
     */
    public static Contract openApi(String file) {
        Path spec = find(file);
        OpenApiInteractionValidator validator = OpenApiInteractionValidator.createForSpecificationUrl(
                        spec.toUri().toString())
                .build();
        return new Contract(file, validator);
    }

    private static Path find(String file) {
        Path folder = Path.of("").toAbsolutePath();
        while (folder != null) {
            Path candidate = folder.resolve("contracts").resolve("openapi").resolve(file);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            folder = folder.getParent();
        }
        throw new IllegalStateException("No contracts/openapi/" + file + " above " + Path.of("").toAbsolutePath());
    }

    /** Throws an {@link AssertionError} that lists every difference between the exchange and the contract. */
    void check(
            String method,
            URI uri,
            Map<String, String> requestHeaders,
            String requestBody,
            int status,
            String responseContentType,
            String responseBody) {
        Request.Method verb = Request.Method.valueOf(method);
        SimpleResponse.Builder response = SimpleResponse.Builder.status(status);
        if (responseContentType != null) {
            response.withContentType(responseContentType);
        }
        if (responseBody != null && !responseBody.isEmpty()) {
            response.withBody(responseBody);
        }
        ValidationReport report = validator.validateResponse(uri.getPath(), verb, response.build());

        if (status >= 200 && status < 300) {
            SimpleRequest.Builder request = new SimpleRequest.Builder(verb, uri.getPath());
            requestHeaders.forEach(request::withHeader);
            if (requestBody != null && !requestBody.isEmpty()) {
                request.withBody(requestBody);
            }
            if (uri.getRawQuery() != null) {
                for (String pair : uri.getRawQuery().split("&")) {
                    int equals = pair.indexOf('=');
                    String name = equals < 0 ? pair : pair.substring(0, equals);
                    String value = equals < 0 ? "" : pair.substring(equals + 1);
                    request.withQueryParam(decode(name), decode(value));
                }
            }
            report = report.merge(validator.validateRequest(request.build()));
        }

        if (report.hasErrors()) {
            String problems = report.getMessages().stream()
                    .filter(message -> message.getLevel() == ValidationReport.Level.ERROR)
                    .map(message -> "  " + message.getKey() + ": " + message.getMessage())
                    .collect(Collectors.joining("\n"));
            throw new AssertionError(method + " " + uri.getPath() + " answered " + status
                    + ", which does not match contracts/openapi/" + file + "\n" + problems + "\nResponse body: "
                    + responseBody);
        }
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}
