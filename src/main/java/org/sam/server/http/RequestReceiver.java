package org.sam.server.http;

import org.sam.server.annotation.handle.*;
import org.sam.server.constant.ContentType;
import org.sam.server.constant.Header;
import org.sam.server.constant.HttpMethod;
import org.sam.server.core.BeanLoader;
import org.sam.server.http.exception.NotFoundHandlerException;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Created by melchor
 * Date: 2020/07/17
 * Time: 1:34 PM
 */
public abstract class RequestReceiver {

    private final Socket connect;

    private List<Class<? extends Annotation>> handleAnnotations =
            Arrays.asList(GetHandle.class, PostHandle.class, PutHandle.class, DeleteHandle.class);

    public RequestReceiver(Socket connect) {

        this.connect = connect;
    }

    public void requestAnalyze() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(connect.getOutputStream());
             BufferedOutputStream dataOut = new BufferedOutputStream(connect.getOutputStream())) {

            Request request = Request.create(in);
            Response response = Response.create(out, dataOut, request.getPath());

            if (!request.getMethod().equals(HttpMethod.GET) && !request.getMethod().equals(HttpMethod.HEAD)) {
                response.methodNotImplemented();
                return;
            }

            executeHandler(request, response);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void executeHandler(Request request, Response response) throws IOException {
        List<Class<?>> handlerClasses = BeanLoader.getHandlerClasses();

        for (Class<?> handlerClass : handlerClasses) {
            String requestPath = request.getPath();
            String handlerPath = handlerClass.getDeclaredAnnotation(Handler.class).value();
            if (!handlerPath.startsWith("/")) handlerPath = "/" + handlerPath;

            if (requestPath.startsWith(handlerPath)) {
                int index = requestPath.indexOf(handlerPath);
                requestPath = requestPath.substring(index + handlerPath.length());
            }

            try {
                Method handlerMethod = findMethod(handlerClass, requestPath);
                Object[] parameters = getHandlerMethodParameters(handlerMethod, request);
                handlerMethod.invoke(handlerClass.newInstance(), parameters);
            } catch (NotFoundHandlerException e) {
                notFound(request, response);
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                e.printStackTrace();
            } finally {
                connect.close();
            }

        }
    }

    private Object[] getHandlerMethodParameters(Method handlerMethod, Request request) {
        Parameter[] parameters = handlerMethod.getParameters();

        Arrays.stream(parameters).forEach(parameter -> {
            String name = parameter.getName();
            Object value = request.getParameter(name);
            Class<?> type = parameter.getType();
        });

        return null;
    }


    private Method findMethod(Class<?> handlerClass, String requestPath) throws NotFoundHandlerException {
        Method[] declaredMethods = handlerClass.getDeclaredMethods();

        for (Method declaredMethod : declaredMethods) {
            Annotation[] declaredAnnotations = declaredMethod.getDeclaredAnnotations();
            for (Annotation declaredAnnotation : declaredAnnotations) {
                for (Class<? extends Annotation> handleAnnotation : handleAnnotations) {
                    if (handleAnnotation.equals(declaredAnnotation.annotationType())) {
                        Method method;
                        try {
                            method = handleAnnotation.getDeclaredMethod("value");
                            Object path = method.invoke(declaredAnnotation);

                            if (requestPath.equals(path)) {
                                return declaredMethod;
                            }
                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        }
        throw new NotFoundHandlerException();
    }

    private void notFound(Request request, Response response) {
        if (!ContentType.JSON.equals(request.getHeader(Header.CONTENT_TYPE))) {
            try {
                response.fileNotFound();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}