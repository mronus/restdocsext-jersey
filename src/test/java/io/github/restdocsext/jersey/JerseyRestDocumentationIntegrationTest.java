/*
 * Copyright 2016-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.restdocsext.jersey;

import java.io.File;
import java.net.URI;
import java.util.regex.Pattern;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.web.bind.annotation.RequestMethod;

import io.github.restdocsext.jersey.test.TestModel;
import io.github.restdocsext.jersey.test.TestResource;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.document;
import static io.github.restdocsext.jersey.JerseyRestDocumentation.documentationConfiguration;
import static io.github.restdocsext.jersey.operation.preprocess.JerseyPreprocessors.binaryParts;
import static io.github.restdocsext.jersey.test.SnippetMatchers.codeBlock;
import static io.github.restdocsext.jersey.test.SnippetMatchers.httpRequest;
import static io.github.restdocsext.jersey.test.SnippetMatchers.snippet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.removeHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;
import static org.springframework.restdocs.templates.TemplateFormats.asciidoctor;

/**
 * Integrations tests for using Restdocs Jersey client for generating Spring RestDocs API
 * documentation snippets.
 *
 * @author Paul Samsotha
 */
public class JerseyRestDocumentationIntegrationTest extends JerseyTest {

    private static final int PORT = 8080;
    private static final String BASE_URI = "http://localhost:" + PORT + "/";

    @Rule
    public JUnitRestDocumentation restDocumentation
            = new JUnitRestDocumentation("build/generated-snippets");

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public JerseyRestDocumentationIntegrationTest() {
        super(new InMemoryTestContainerFactory());
    }

    @Override
    public URI getBaseUri() {
        return URI.create(BASE_URI);
    }

    @Override
    public ResourceConfig configure() {
        return new ResourceConfig(TestResource.class)
                .register(MultiPartFeature.class)
                .register(LoggingFilter.class);
    }

    @Test
    public void default_snippets_generated() {
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("default"))
                .path("test/get-default")
                .request().get();
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertExpectedSnippetFilesExist(new File("build/generated-snippets/default"),
                "http-request.adoc", "http-response.adoc", "curl-request.adoc");
    }

    @Test
    public void curl_snippet_with_content() {
        final String contentType = "text/plain; charset=UTF-8";
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("curl-snippet-with-content",
                                preprocessRequest(removeHeaders("User-Agent"))))
                .path("test/post-simple")
                .request("text/plain")
                .post(Entity.entity("content", contentType));
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertThat(
                new File("build/generated-snippets/curl-snippet-with-content/curl-request.adoc"),
                is(snippet(asciidoctor()).withContents(codeBlock(asciidoctor(), "bash")
                                .content(
                                    "$ curl 'http://localhost:" + PORT + "/test/post-simple' -i -X POST \\\n"
                                        + "    -H 'Accept: text/plain' \\\n"
                                        + "    -H 'Content-Type: " + contentType + "' \\\n"
                                        + "    -d 'content'"))));
    }

    @Test
    public void curl_get_with_query_string() {
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("curl-get-with-query-string",
                                preprocessRequest(removeHeaders("User-Agent"))))
                .path("test/get-default")
                .queryParam("a", "alpha")
                .queryParam("b", "bravo")
                .request()
                .get();
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertThat(new File("build/generated-snippets/curl-get-with-query-string/curl-request.adoc"),
                is(snippet(asciidoctor()).withContents(codeBlock(asciidoctor(), "bash").content(
                                        "$ curl "
                                        + "'http://localhost:8080/test/get-default?a=alpha&b=bravo' -i"))));
    }

    @Test
    public void query_parameters_snippet() {
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("query-parameters",
                                requestParameters(
                                        parameterWithName("a").description("'a' description"),
                                        parameterWithName("b").description("'b' description"))))
                .path("test/get-default")
                .queryParam("a", "alpha")
                .queryParam("b", "bravo")
                .request().get();
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();

        assertExpectedSnippetFilesExist(new File("build/generated-snippets/query-parameters"),
                "curl-request.adoc", "http-request.adoc", "http-request.adoc", "request-parameters.adoc");
    }

    /*
     * Expected {@code SnippetException}. It seems the exception gets wrapped in a jersey
     * {@code ProcessingException}.
     */
    @Test
    public void missing_query_parameters() {
        this.thrown.expect(ProcessingException.class);

        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("missing-query-parameters",
                                requestParameters(
                                        parameterWithName("a").description("'a' description"))))
                .path("test/get-default")
                .queryParam("a", "alpha")
                .queryParam("b", "bravo")
                .request().get();

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();
    }

    @Test
    public void path_parameters_snippet() {
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("path-parameters",
                                pathParameters(
                                        parameterWithName("param1").description("param1 description"),
                                        parameterWithName("param2").description("param2 description"))))
                .path("test/path-params").path("{param1}").path("sub").path("{param2}")
                .resolveTemplate("param1", "value1")
                .resolveTemplate("param2", "value2")
                .request().get();

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertExpectedSnippetFilesExist(new File("build/generated-snippets/path-parameters"),
                "curl-request.adoc", "http-request.adoc", "http-request.adoc", "path-parameters.adoc");
    }

    @Test
    public void missing_path_parameter_descriptor() {
        this.thrown.expect(ProcessingException.class);

        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("mssing-path-parameter-descriptor",
                                pathParameters(
                                        parameterWithName("param1").description("param1 description"))))
                .path("test/path-params").path("{param1}").path("sub").path("{param2}")
                .resolveTemplate("param1", "value1")
                .resolveTemplate("param2", "value2")
                .request().get();

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    }

    @Test
    public void form_parameters_snippet() {
        final Form form = new Form();
        form.param("a", "alpha");
        form.param("b", "bravo");
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("form-parameters",
                                requestParameters(
                                        parameterWithName("a").description("a description"),
                                        parameterWithName("b").description("b description"))))
                .path("test/post-form")
                .request()
                .post(Entity.form(form));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();

        assertExpectedSnippetFilesExist(new File("build/generated-snippets/form-parameters"),
                "curl-request.adoc", "http-request.adoc", "http-request.adoc",
                "request-parameters.adoc");
    }

    @Test
    public void missing_form_parameters() {
        this.thrown.expect(ProcessingException.class);

        final Form form = new Form();
        form.param("a", "alpha");
        form.param("b", "bravo");
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("missing-form-parameters",
                                requestParameters(
                                        parameterWithName("a").description("a description"))))
                .path("test/post-form")
                .request()
                .post(Entity.form(form));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();
    }

    @Test
    public void curl_with_form_parameters() {
        final Form form = new Form();
        form.param("a", "alpha");
        form.param("b", "bravo");
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("curl-with-form-parameters",
                                preprocessRequest(removeHeaders("User-Agent"))))
                .path("test/post-form")
                .request()
                .post(Entity.form(form));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();

        assertThat(new File("build/generated-snippets/curl-with-form-parameters/curl-request.adoc"),
                is(snippet(asciidoctor()).withContents(
                                codeBlock(asciidoctor(), "bash").content(
                                    "$ curl 'http://localhost:8080/test/post-form' -i -X POST \\\n"
                                        + "    -H 'Content-Type: application/x-www-form-urlencoded' \\\n"
                                        + "    -d 'a=alpha&b=bravo'"))));
    }

    @Test
    public void curl_post_with_query_and_form() {
        final Form form = new Form();
        form.param("a", "alpha");
        form.param("b", "bravo");
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("curl-post-with-query-and-form",
                                preprocessRequest(removeHeaders("User-Agent"))))
                .path("test/post-form")
                .queryParam("c", "charlie")
                .queryParam("d", "delta")
                .request()
                .post(Entity.form(form));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();

        assertThat(new File("build/generated-snippets/curl-post-with-query-and-form/curl-request.adoc"),
                is(snippet(asciidoctor()).withContents(
                        codeBlock(asciidoctor(), "bash").content(
                                "$ curl 'http://localhost:8080/test/post-form?c=charlie&d=delta' -i -X POST \\\n"
                                    + "    -H 'Content-Type: application/x-www-form-urlencoded' \\\n"
                                    + "    -d 'a=alpha&b=bravo'"))));
    }

    @Test
    public void json_request_fields() {
        final TestModel bean = new TestModel(0, "michael", "jordan");
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("json-request-fields",
                                requestFields(
                                        fieldWithPath("id").description("id field"),
                                        fieldWithPath("fname").description("firstName field"),
                                        fieldWithPath("lname").description("lastName field"))))
                .path("test/post-json")
                .request()
                .post(Entity.json(bean));

        assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));
        response.close();

        assertExpectedSnippetFilesExist(new File("build/generated-snippets/json-request-fields"),
                "http-request.adoc", "http-response.adoc", "curl-request.adoc",
                "request-fields.adoc");
    }

    @Test
    public void missing_json_request_fields() {
        this.thrown.expect(ProcessingException.class);

        TestModel bean = new TestModel(1, "michael", "jordan");
        Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("missing-json-request-fields",
                                requestFields(
                                        fieldWithPath("id").description("id field"))))
                .path("test/post-json")
                .request()
                .post(Entity.json(bean));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();
    }

    @Test
    public void json_response_fields() throws Exception {
        TestModel bean = new TestModel(1, "michael", "jordan");
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("json-response-fields",
                                responseFields(
                                        fieldWithPath("id").description("id field"),
                                        fieldWithPath("fname").description("firstName field"),
                                        fieldWithPath("lname").description("lastName field"))))
                .path("test/post-json")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.json(bean));

        assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));
        bean = response.readEntity(TestModel.class);
        assertThat(bean.getId(), is(1));
        assertThat(bean.getFname(), is(equalTo("michael")));
        assertThat(bean.getLname(), is(equalTo("jordan")));
        response.close();

        assertExpectedSnippetFilesExist(new File("build/generated-snippets/json-response-fields"),
                "http-request.adoc", "http-response.adoc", "curl-request.adoc",
                "response-fields.adoc");
    }

    @Test
    public void missing_json_response_fields() throws Exception {
        this.thrown.expect(ProcessingException.class);

        TestModel bean = new TestModel(1, "michael", "jordan");
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("json-response-fields",
                                responseFields(
                                        fieldWithPath("id").description("id field"))))
                .path("test/post-json")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.json(bean));

        assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));
        bean = response.readEntity(TestModel.class);
        assertThat(bean.getId(), is(1));
        assertThat(bean.getFname(), is(equalTo("michael")));
        assertThat(bean.getLname(), is(equalTo("jordan")));
        response.close();
    }

    @Test
    public void multipart_request() {
        final MultiPart multiPart = new FormDataMultiPart()
                .field("field1", "field1Data")
                .field("field2", "filed2Data")
                .bodyPart(new FileDataBodyPart("file", new File("src/test/resources/images/image.png")));
        final Response response = target()
                .register(MultiPartFeature.class)
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("multipart-request",
                                preprocessRequest(
                                        binaryParts().field("file", "<<image-data>>"),
                                        removeHeaders("User-Agent", "Content-Type")),
                                requestParts(
                                        partWithName("field1").description("field1 description"),
                                        partWithName("field2").description("field2 description"),
                                        partWithName("file").description("image file"))))
                .path("test/post-multipart")
                .request()
                .post(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA));

        assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));
        assertExpectedSnippetFilesExist(new File("build/generated-snippets/multipart-request"),
                "http-request.adoc", "http-response.adoc", "curl-request.adoc", "request-parts.adoc");
    }

    @Test
    public void parameterizedOutputDirectory() {
        target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("{method-name}"))
                .path("test/get-default")
                .request()
                .get().close();

        assertExpectedSnippetFilesExist(new File(
                "build/generated-snippets/parameterized-output-directory"),
                "http-request.adoc", "http-response.adoc", "curl-request.adoc");
    }

    @Test
    public void multiStep() {
        WebTarget target = target("test/get-default")
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("{method-name}-{step}"));

        target.request().get().close();
        assertExpectedSnippetFilesExist(
                new File("build/generated-snippets/multi-step-1/"), "http-request.adoc",
                "http-response.adoc", "curl-request.adoc");

        target.request().get().close();
        assertExpectedSnippetFilesExist(
                new File("build/generated-snippets/multi-step-2/"), "http-request.adoc",
                "http-response.adoc", "curl-request.adoc");

        target.request().get().close();
        assertExpectedSnippetFilesExist(
                new File("build/generated-snippets/multi-step-3/"), "http-request.adoc",
                "http-response.adoc", "curl-request.adoc");
    }

    @Test
    public void additionalSnippets() {
        final JerseyRestDocumentationFilter documentation = document("{method-name}-{step}");
        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(documentation)
                .path("test/get-default")
                .register(documentation.document(
                                requestHeaders(
                                        headerWithName("foo").description("bar"))))
                .request()
                .header("foo", "bar")
                .get();

        assertThat(response.getStatus(), is(200));
        assertExpectedSnippetFilesExist(
                new File("build/generated-snippets/additional-snippets-1/"),
                "http-request.adoc", "http-response.adoc", "curl-request.adoc",
                "request-headers.adoc");
    }

    @Test
    public void preprocessed_request() {
        final String json = "{\"a\":\"alpha\"}";
        final Pattern pattern = Pattern.compile("(\"alpha\")");

        final Response response = target()
                .register(documentationConfiguration(this.restDocumentation))
                .register(document("original-request"))
                .register(document("preprocessed-request",
                                preprocessRequest(prettyPrint(),
                                        removeHeaders("a", "Host", "Content-Length", "User-Agent"),
                                        replacePattern(pattern, "\"<<beta>>\""))))
                .path("test/post-random-json")
                .request()
                .header("a", "alpha").header("b", "bravo")
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(json, MediaType.APPLICATION_JSON));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();

        final String prettyPrinted = String.format("{%n  \"a\" : \"<<beta>>\"%n}");
        assertThat(new File("build/generated-snippets/preprocessed-request/http-request.adoc"),
                is(snippet(asciidoctor()).withContents(
                                httpRequest(asciidoctor(), RequestMethod.POST, "/test/post-random-json")
                                .header("b", "bravo")
                                .header("Accept", MediaType.APPLICATION_JSON)
                                .header("Content-Type", "application/json")
                                .content(prettyPrinted))));
    }

    @Test
    public void change_uri_scheme_host_port() {
        Response response = target()
                .register(documentationConfiguration(this.restDocumentation)
                        .uris().withScheme("https").withHost("testing.com").removePort())
                .register(document("change-uri", preprocessRequest(prettyPrint(),
                        removeHeaders("a", "Host", "Content-Length", "User-Agent"))))
                .path("/test/post-simple")
                .request("text/plain")
                .post(Entity.text("testing"));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();

        assertThat(
                new File("build/generated-snippets/change-uri/curl-request.adoc"),
                is(snippet(asciidoctor()).withContents(codeBlock(asciidoctor(), "bash")
                        .content("$ curl 'https://testing.com/test/post-simple' -i -X POST \\\n"
                            + "    -H 'Accept: text/plain' \\\n"
                            + "    -H 'Content-Type: text/plain' \\\n"
                            + "    -d 'testing'"))));

        response = target()
                .register(documentationConfiguration(this.restDocumentation)
                        .uris().withScheme("https").withHost("testing.com").withPort(80))
                .register(document("change-uri", preprocessRequest(prettyPrint(),
                        removeHeaders("a", "Host", "Content-Length", "User-Agent"))))
                .path("/test/post-simple")
                .request("text/plain")
                .post(Entity.text("testing"));

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        response.close();

        assertThat(
                new File("build/generated-snippets/change-uri/curl-request.adoc"),
                is(snippet(asciidoctor()).withContents(codeBlock(asciidoctor(), "bash")
                        .content("$ curl 'https://testing.com:80/test/post-simple' -i -X POST \\\n"
                            + "    -H 'Accept: text/plain' \\\n"
                            + "    -H 'Content-Type: text/plain' \\\n"
                            + "    -d 'testing'"))));

    }

    private void assertExpectedSnippetFilesExist(File directory, String... snippets) {
        for (String snippet : snippets) {
            File snippetFile = new File(directory, snippet);
            assertTrue("Snippet " + snippetFile + " not found", snippetFile.isFile());
        }
    }
}
