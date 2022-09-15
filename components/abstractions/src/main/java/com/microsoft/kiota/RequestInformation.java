package com.microsoft.kiota;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.SerializationWriter;

import com.github.hal4j.uritemplate.URITemplate;

/** This class represents an abstract HTTP request. */
public class RequestInformation {
    /** The url template for the current request */
    public String urlTemplate;
    /** The path parameters for the current request */
    public HashMap<String, Object> pathParameters = new HashMap<>();
    private URI uri;
    /** Gets the URI of the request. 
     * @throws URISyntaxException
     * @throws IllegalStateException
     */
    @Nullable
    public URI getUri() throws URISyntaxException,IllegalStateException{
        if(uri != null) {
            return uri;
        } else if(pathParameters.containsKey(RAW_URL_KEY) &&
            pathParameters.get(RAW_URL_KEY) instanceof String) {
            setUri(new URI((String)pathParameters.get(RAW_URL_KEY)));
            return uri;
        } else {
            Objects.requireNonNull(urlTemplate);
            Objects.requireNonNull(queryParameters);
            if(!pathParameters.containsKey("baseurl") && urlTemplate.toLowerCase().contains("{+baseurl}"))
                throw new IllegalStateException("PathParameters must contain a value for \"baseurl\" for the url to be built.");

            final URITemplate template = new URITemplate(urlTemplate)
                            .expandOnly(new HashMap<String, Object>(queryParameters) {{
                                putAll(pathParameters);
                            }});
            return template.toURI();
        }
    }
    /** Sets the URI of the request. */
    public void setUri(@Nonnull final URI uri) {
        this.uri = Objects.requireNonNull(uri);
        if(queryParameters != null) {
            queryParameters.clear();
        }
        if(pathParameters != null) {
            pathParameters.clear();
        }
    }
    private static String RAW_URL_KEY = "request-raw-url";
    /** The HTTP method for the request */
    @Nullable
    public HttpMethod httpMethod;

    private HashMap<String, Object> queryParameters = new HashMap<>();
    /**
     * Adds query parameters to the request based on the object passed in and its fields.
     * @param object The object to add the query parameters from.
     */
    public void addQueryParameters(@Nullable final Object parameters) {
        if (parameters == null) return;
        final Field[] fields = parameters.getClass().getFields();
        for(final Field field : fields) {
            try {
                final Object value = field.get(parameters);
                String name = field.getName();
                if (field.isAnnotationPresent(QueryParameter.class)) {
                    final String annotationName = field.getAnnotation(QueryParameter.class).name();
                    if(annotationName != null && !annotationName.isEmpty()) {
                        name = annotationName;
                    }
                }
                if(value != null) {
                    if(value.getClass().isArray()) {
                        queryParameters.put(name, Arrays.asList((Object[])value));
                    } else {
                        queryParameters.put(name, value);
                    }
                }
            } catch (IllegalAccessException ex) {
                //TODO log
            }
        }
    }
    /**
     * Adds query parameters to the request.
     * @param name The name of the query parameter.
     * @param value The value to add the query parameters.
     */
    public void addQueryParameter(@Nonnull final String name, @Nullable final Object value) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        queryParameters.put(name, value);
    }
    /**
     * Removes a query parameter from the request.
     * @param name The name of the query parameter to remove.
     */
    public void removeQueryParameter(@Nonnull final String name) {
        Objects.requireNonNull(name);
        queryParameters.remove(name);
    }
    /**
     * Gets the query parameters for the request.
     * @return The query parameters for the request.
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public Map<String, Object> getQueryParameters() {
        return (Map<String, Object>) queryParameters.clone();
    }
    private HashMap<String, String> headers = new HashMap<>();
    /**
     * Adds headers to the current request.
     * @param headersToAdd headers to add to the current request.
     */
    public void addRequestHeaders(@Nullable final Map<String, String> headersToAdd) {
        if (headersToAdd == null || headersToAdd.isEmpty()) return;
        headersToAdd.entrySet()
                    .stream()
                    .forEach(entry -> this.addRequestHeader(entry.getKey(), entry.getValue()));
    }
    /**
     * Adds a header to the current request.
     * @param key the key of the header to add.
     * @param value the value of the header to add.
     */
    public void addRequestHeader(@Nonnull final String key, @Nonnull final String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        headers.put(key.toLowerCase(), value);
    }
    /**
     * Removes a request header from the current request.
     * @param key the key of the header to remove.
     */
    public void removeRequestHeader(@Nonnull final String key) {
        Objects.requireNonNull(key);
        headers.remove(key.toLowerCase());
    }
    /** 
     * Gets the request headers the for current request
     * @return the request headers for the current request.
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public Map<String, String> getRequestHeaders() {
        return (Map<String, String>) headers.clone();
    }
    /** The Request Body. */
    @Nullable
    public InputStream content;
    private HashMap<String, RequestOption> _requestOptions = new HashMap<>();
    /**
     * Gets the request options for this request. Options are unique by type. If an option of the same type is added twice, the last one wins.
     * @return the request options for this request.
     */
    public Collection<RequestOption> getRequestOptions() { return _requestOptions.values(); }
    /**
     * Adds request options to this request.
     * @param options the request options to add.
     */
    public void addRequestOptions(@Nullable final Collection<RequestOption> options) { 
        if(options == null || options.isEmpty()) return;
        for(final RequestOption option : options) {
            _requestOptions.put(option.getClass().getCanonicalName(), option);
        }
    }
    /**
     * Removes a request option from this request.
     * @param option the request option to remove.
     */
    public void removeRequestOptions(@Nullable final RequestOption... options) {
        if(options == null || options.length == 0) return;
        for(final RequestOption option : options) {
            _requestOptions.remove(option.getClass().getCanonicalName());
        }
    }
    private static String binaryContentType = "application/octet-stream";
    private static String contentTypeHeader = "Content-Type";
    /**
     * Sets the request body to be a binary stream.
     * @param value the binary stream
     */
    public void setStreamContent(@Nonnull final InputStream value) {
        Objects.requireNonNull(value);
        this.content = value;
        headers.put(contentTypeHeader, binaryContentType);
    }
    /**
     * Sets the request body from a model with the specified content type.
     * @param values the models.
     * @param contentType the content type.
     * @param requestAdapter The adapter service to get the serialization writer from.
     * @param <T> the model type.
     */
    public <T extends Parsable> void setContentFromParsable(@Nonnull final RequestAdapter requestAdapter, @Nonnull final String contentType, @Nonnull final T... values) {
        try(final SerializationWriter writer = getSerializationWriter(requestAdapter, contentType, values)) {
            headers.put(contentTypeHeader, contentType);
            if(values.length == 1) 
                writer.writeObjectValue(null, values[0]);
            else
                writer.writeCollectionOfObjectValues(null, Arrays.asList(values));
            this.content = writer.getSerializedContent();
        } catch (IOException ex) {
            throw new RuntimeException("could not serialize payload", ex);
        }
    }
    private <T> SerializationWriter getSerializationWriter(@Nonnull final RequestAdapter requestAdapter, @Nonnull final String contentType, @Nonnull final T... values)
    {
        Objects.requireNonNull(requestAdapter);
        Objects.requireNonNull(values);
        Objects.requireNonNull(contentType);
        if(values.length == 0) throw new RuntimeException("values cannot be empty");

        return requestAdapter.getSerializationWriterFactory().getSerializationWriter(contentType);
    }
    /**
     * Sets the request body from a scalar value with the specified content type.
     * @param values the scalar values to serialize.
     * @param contentType the content type.
     * @param requestAdapter The adapter service to get the serialization writer from.
     * @param <T> the model type.
     */
    public <T> void setContentFromScalar(@Nonnull final RequestAdapter requestAdapter, @Nonnull final String contentType, @Nonnull final T... values) {
        try(final SerializationWriter writer = getSerializationWriter(requestAdapter, contentType, values)) {
            headers.put(contentTypeHeader, contentType);
            if(values.length == 1) {
                final T value = values[0];
                final Class<?> valueClass = value.getClass();
                if(valueClass.equals(String.class))
                    writer.writeStringValue(null, (String)value);
                else if(valueClass.equals(Boolean.class))
                    writer.writeBooleanValue(null, (Boolean)value);
                else if(valueClass.equals(Byte.class))
                    writer.writeByteValue(null, (Byte)value);
                else if(valueClass.equals(Short.class))
                    writer.writeShortValue(null, (Short)value);
                else if(valueClass.equals(BigDecimal.class))
                    writer.writeBigDecimalValue(null, (BigDecimal)value);
                else if(valueClass.equals(Float.class))
                    writer.writeFloatValue(null, (Float)value);
                else if(valueClass.equals(Long.class))
                    writer.writeLongValue(null, (Long)value);
                else if(valueClass.equals(Integer.class))
                    writer.writeIntegerValue(null, (Integer)value);
                else if(valueClass.equals(UUID.class))
                    writer.writeUUIDValue(null, (UUID)value);
                else if(valueClass.equals(OffsetDateTime.class))
                    writer.writeOffsetDateTimeValue(null, (OffsetDateTime)value);
                else if(valueClass.equals(LocalDate.class))
                    writer.writeLocalDateValue(null, (LocalDate)value);
                else if(valueClass.equals(LocalTime.class))
                    writer.writeLocalTimeValue(null, (LocalTime)value);
                else if(valueClass.equals(Period.class))
                    writer.writePeriodValue(null, (Period)value);
                else
                    throw new RuntimeException("unknown type to serialize " + valueClass.getName());
            } else
                writer.writeCollectionOfPrimitiveValues(null, Arrays.asList(values));
            this.content = writer.getSerializedContent();
        } catch (IOException ex) {
            throw new RuntimeException("could not serialize payload", ex);
        }
    }
}
