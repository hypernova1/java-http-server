package org.sam.server.core;

import org.sam.server.exception.HandlerNotFoundException;
import org.sam.server.http.HttpRequest;
import org.sam.server.http.HttpResponse;
import org.sam.server.http.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by melchor
 * Date: 2020/07/17
 * Time: 1:34 PM
 */
public class HttpLauncher {

    private static final Logger logger = LoggerFactory.getLogger(HttpLauncher.class);

    public static void execute(Socket connect) {
        try {
            HttpRequest httpRequest = Request.create(connect.getInputStream());
            HttpResponse httpResponse = HttpResponse.create(connect.getOutputStream(), httpRequest.getPath());
            findHandler(httpRequest, httpResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void findHandler(HttpRequest httpRequest, HttpResponse httpResponse) throws IOException {
        try {
            if (httpRequest.getPath().startsWith("/resources")) {
                httpResponse.getStaticResources();
                return;
            }
            HandlerInfo handlerInfo = new HandlerFinder(httpRequest, httpResponse).createHandlerInfo();
            new HandlerExecutor(httpRequest, httpResponse, handlerInfo).execute();
        } catch (HandlerNotFoundException e) {
            httpResponse.fileNotFound();
            throw new IOException(e);
        }
    }
}