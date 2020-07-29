package org.sam.server.http;

import org.sam.server.constant.ContentType;
import org.sam.server.constant.HttpMethod;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by melchor
 * Date: 2020/07/22
 * Time: 5:19 PM
 */
public interface Request {

    static HttpRequest create(InputStream in) {
        return new Request.UrlParser(in).createRequest();
    }

    String getPath();

    HttpMethod getMethod();

    String getParameter(String key);

    Map<String, String> getParameters();

    Set<String> getParameterNames();

    Set<String> getHeaderNames();

    String getHeader(String key);

    Map<String, Object> getAttributes();

    String getJson();

    Set<Cookie> getCookies();

    class UrlParser {
        protected String path;
        protected HttpMethod method;
        protected Map<String, String> headers = new HashMap<>();
        protected Map<String, String> parameters = new HashMap<>();
        protected Map<String, Object> attributes = new HashMap<>();
        protected String json;
        protected Set<Cookie> cookies = new HashSet<>();
        protected Map<String, MultipartFile> files = new HashMap<>();

        public UrlParser(InputStream in) {
            parse(in);
        }

        private void parse(InputStream in) {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(in));

                String input = br.readLine();
                StringTokenizer parse = new StringTokenizer(input);
                String method = parse.nextToken().toUpperCase();
                String requestPath = parse.nextToken().toLowerCase();
                String query = parsePathAndGetQuery(requestPath);

                if (!query.isEmpty()) {
                    this.parameters = parseQuery(query);
                }

                parseHeaders(br);
                parseMethod(method);

                String contentType = headers.get("content-type") != null ? headers.get("content-type") : "";
                String boundary = null;
                if (contentType.startsWith(ContentType.MULTIPART_FORM_DATA.getValue())) {
                    boundary = "--" + contentType.split("; ")[1].split("=")[1];
                }

                if (HttpMethod.get(method).equals(HttpMethod.POST) ||
                        HttpMethod.get(method).equals(HttpMethod.PUT) ||
                        ContentType.APPLICATION_JSON.getValue().equals(contentType)) {

                    String temp;
                    StringBuilder requestBody = new StringBuilder();
                    while ((temp = br.readLine()) != null) {
                        requestBody.append(temp).append("\n");
                    }
                    if (ContentType.APPLICATION_JSON.getValue().equals(contentType) && this.attributes == null) {
                        this.json = requestBody.toString();
                    }
                    if (boundary != null) {
                        parseMultipartBody(requestBody.toString(), boundary);
                    } else {
                        this.attributes = parseRequestBody(requestBody.toString());
                    }
                }
            } catch (IOException e) {
                System.out.println("terminate thread..");
                e.printStackTrace();
            }
        }

        private void parseHeaders(BufferedReader br) {
            try {
                String s = br.readLine();
                while (!s.trim().equals("")) {
                    int index = s.indexOf(": ");
                    String key = s.substring(0, index).toLowerCase();
                    String value = s.substring(index + 2);
                    if ("cookie".equals(key)) {
                        this.cookies = CookieStore.parseCookie(value);
                        s = br.readLine();
                        continue;
                    }

                    this.headers.put(key, value);
                    s = br.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void parseMethod(String method) {
            this.method = HttpMethod.get(method);
        }

        private String parsePathAndGetQuery(String requestPath) {
            int index = requestPath.indexOf("?");
            if (index != -1) {
                this.path = requestPath.substring(0, index);
                return requestPath.substring(index + 1);
            }
            this.path = requestPath;
            return "";
        }

        private Map<String, String> parseQuery(String parameters) {
            Map<String, String> map = new HashMap<>();
            String[] rawParameters = parameters.split("&");
            Arrays.stream(rawParameters).forEach(parameter -> {
                String[] parameterPair = parameter.split("=");
                String name = parameterPair[0];
                String value = null;
                if (parameterPair.length == 2) {
                    value = parameterPair[1];
                }
                map.put(name, value);
            });
            return map;
        }

        private Map<String, Object> parseRequestBody(String requestBody) {
            if (requestBody.startsWith("{") && requestBody.endsWith("}")) return null;
            Map<String, Object> map = new HashMap<>();
            String[] rawParameters = requestBody.split("&");
            Arrays.stream(rawParameters).forEach(parameter -> {
                String[] parameterPair = parameter.split("=");
                String name = parameterPair[0];
                String value = null;
                if (parameterPair.length == 2) {
                    value = parameterPair[1];
                }
                map.put(name, value);
            });
            return map;
        }

        private void parseMultipartBody(String requestBody, String boundary) {
            String[] rawFormDataList = requestBody.replace("/\\s/g", "").split(boundary);
            List<String> multipartList = Arrays.asList(rawFormDataList);
            multipartList = multipartList.subList(1, multipartList.size() - 1);

            List<List<String>> multipartFormDataList = multipartList.stream().map(multipart -> {
                List<String> lines = Arrays.asList(multipart.split("\\n\\n"));
                lines = lines.stream().filter(line -> !line.isEmpty()).collect(Collectors.toList());
                return lines;
            }).collect(Collectors.toList());

            Pattern pattern = Pattern.compile("\\\"(.*?)\\\"");

            multipartFormDataList.forEach(multipartFormData -> {
                String[] descriptions = multipartFormData.get(0).trim().split("\\n");

                String name = descriptions[0].split("; ")[1];
                Matcher matcher = pattern.matcher(name);
                while(matcher.find()) {
                    name = matcher.group().replace("\"", "");
                }
                if (descriptions.length == 1) {
                    String value = multipartFormData.get(1).trim();
                    attributes.put(name, value);
                } else {
                    String fileName = descriptions[0].split("; ")[2];
                    matcher = pattern.matcher(fileName);
                    while (matcher.find()) fileName = matcher.group().replace("\"", "");
                    String fileType = descriptions[1].split(": ")[1];
                    String fileData = multipartFormData.get(1);
                    MultipartFile file = new MultipartFile(fileName, fileType, fileData);

                    files.put(name, file);
                }
            });
        }

        public HttpRequest createRequest() {
            Map<String, String> headers = this.headers;
            HttpMethod method = this.method;
            String path = this.path;
            Map<String, String> parameters = this.parameters;
            Map<String, Object> attributes = this.attributes;
            String json = this.json;
            Set<Cookie> cookies = this.cookies;

            String contentType = headers.get("content-type") != null ? headers.get("content-type") : "";
            if (contentType.startsWith(ContentType.MULTIPART_FORM_DATA.getValue())) {
                return new HttpMultipartRequest(path, method, headers, parameters, attributes, json, cookies, files);
            }
            return new HttpRequest(path, method, headers, parameters, attributes, json, cookies);
        }
    }
}
