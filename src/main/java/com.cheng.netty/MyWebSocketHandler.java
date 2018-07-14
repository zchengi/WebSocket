package com.cheng.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.Date;

/**
 * 接收/处理/响应客户端WebSocket请求的核心业务处理类
 *
 * @author cheng
 *         2018/7/13 18:08
 */
public class MyWebSocketHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;

    private static final String WEB_SOCKET_URL = "ws://localhost:8888/websocket";

    /**
     * 客户端与服务端创建连接的时候调用
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        NettyConfig.group.add(ctx.channel());
        System.out.println("客户端与服务端连接开启...");
    }

    /**
     * 客户端与服务器断开连接的时候调用
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyConfig.group.remove(ctx.channel());
        System.out.println("客户端与服务端连接关闭");
    }

    /**
     * 服务端接收客户端发送过来的数据结束之后调用
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    /**
     * 工程出现异常的时候调用
     *
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * 服务端处理客户端WebSocket请求的核心方法
     *
     * @param context
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext context, Object msg) {

        if (msg instanceof FullHttpRequest) {
            // 处理客户端向服务端发起http握手请求的业务
            handHttpRequest(context, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            // 处理WebSocket连接业务
            handWebSocketFrame(context, (WebSocketFrame) msg);
        }
    }

    /**
     * 处理客户端向服务端发起http握手请求的业务
     *
     * @param ctx
     * @param req
     */
    private void handHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {

        if (!req.decoderResult().isSuccess() || !("websocket".equals(req.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, req,
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(WEB_SOCKET_URL, null, false);

        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    /**
     * 服务端向客户端响应消息
     *
     * @param ctx
     * @param req
     * @param res
     */
    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {

        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }

        // 服务端向客户端发送数据
        ChannelFuture future = ctx.channel().writeAndFlush(res);
        if (res.status().code() != 200) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 处理客户端与服务端之间的websockt业务
     *
     * @param ctx
     * @param frame
     */
    private void handWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {

        // 判断是否是关闭WebSocket的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
        }

        // 判断是否是ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        // 判断是否是二进制消息，如果是，抛出异常，
        if (!(frame instanceof TextWebSocketFrame)) {
            System.out.println("目前我们不支持二进制消息");
            throw new RuntimeException("[" + this.getClass().getName() + "] 不支持消息");
        }

        // 返回应答消息
        // 获取客户端向服务端发送的消息
        String request = ((TextWebSocketFrame) frame).text();
        System.out.println("服务端收到客户端的消息===>>>" + request);
        TextWebSocketFrame tws = new TextWebSocketFrame(new Date().toString()
                + ctx.channel().id()
                + "===>>>" + request);

        // 群发，服务端向每个连接上来的客户端群发消息
        NettyConfig.group.writeAndFlush(tws);
    }
}
