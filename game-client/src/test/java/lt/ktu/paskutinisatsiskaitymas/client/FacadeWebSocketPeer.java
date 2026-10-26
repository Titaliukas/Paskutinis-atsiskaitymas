package lt.ktu.paskutinisatsiskaitymas.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lt.ktu.paskutinisatsiskaitymas.protocol.JsonMessageCodec;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;

/** Minimal local wire peer: exercises the real JDK client without a server-module dependency. */
final class FacadeWebSocketPeer implements AutoCloseable {
    private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "facade-test-peer"));
    private final CompletableFuture<Socket> accepted = new CompletableFuture<>();
    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    private final JsonMessageCodec codec = new JsonMessageCodec();
    final BlockingQueue<Message> received = new LinkedBlockingQueue<>();
    final CompletableFuture<Void> pong = new CompletableFuture<>();
    final CompletableFuture<Void> clientClosed = new CompletableFuture<>();
    private volatile boolean closing;
    private volatile Socket socket;

    FacadeWebSocketPeer() throws Exception {
        worker.submit(() -> {
            try (Socket client = server.accept()) {
                socket = client;
                client.setSoTimeout(10000);
                DataInputStream input = new DataInputStream(new BufferedInputStream(client.getInputStream()));
                upgrade(input, client);
                accepted.complete(client);
                readFrames(input);
                finished.complete(null);
            } catch (Exception exception) {
                if (closing) {
                    finished.complete(null);
                } else {
                    accepted.completeExceptionally(exception);
                    finished.completeExceptionally(exception);
                }
            }
        });
    }

    URI address() {
        return URI.create("ws://localhost:" + server.getLocalPort() + "/game");
    }

    void send(Message message) throws Exception {
        frame(1, codec.encode(message).getBytes(StandardCharsets.UTF_8));
    }

    void ping() throws Exception {
        frame(9, new byte[0]);
    }

    private synchronized void frame(int opcode, byte[] payload) throws Exception {
        DataOutputStream output = new DataOutputStream(accepted.get(5, TimeUnit.SECONDS).getOutputStream());
        output.writeByte(0x80 | opcode);
        if (payload.length < 126) {
            output.writeByte(payload.length);
        } else {
            output.writeByte(126);
            output.writeShort(payload.length);
        }
        output.write(payload);
        output.flush();
    }

    private void readFrames(DataInputStream input) throws Exception {
        while (!closing) {
            int first = input.read();
            if (first == -1) {
                clientClosed.complete(null);
                return;
            }
            int second = input.readUnsignedByte();
            int length = second & 0x7f;
            if (length == 126) {
                length = input.readUnsignedShort();
            } else if (length == 127) {
                length = Math.toIntExact(input.readLong());
            }
            if (length > JsonMessageCodec.MAX_MESSAGE_CHARACTERS * 4 || (second & 0x80) == 0) {
                throw new IllegalStateException("Invalid client frame");
            }
            byte[] mask = new byte[4];
            input.readFully(mask);
            byte[] payload = new byte[length];
            input.readFully(payload);
            for (int index = 0; index < length; index++) {
                payload[index] ^= mask[index % 4];
            }
            switch (first & 0x0f) {
                case 1 -> received.add(codec.decode(new String(payload, StandardCharsets.UTF_8)));
                case 8 -> {
                    clientClosed.complete(null);
                    return;
                }
                case 10 -> pong.complete(null);
                default -> throw new IllegalStateException("Unexpected client opcode");
            }
        }
    }

    private static void upgrade(DataInputStream input, Socket client) throws Exception {
        StringBuilder header = new StringBuilder();
        while (!header.toString().endsWith("\r\n\r\n")) {
            int value = input.read();
            if (value == -1 || header.length() > 16384) {
                throw new EOFException("Missing upgrade request");
            }
            header.append((char) value);
        }
        String key = header.toString().lines()
                .filter(line -> line.regionMatches(true, 0, "Sec-WebSocket-Key:", 0, 18))
                .findFirst().orElseThrow().substring(18).trim();
        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        client.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                + "Connection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().flush();
    }

    @Override
    public void close() throws Exception {
        closing = true;
        server.close();
        Socket client = socket;
        if (client != null) {
            client.close();
        }
        worker.shutdownNow();
        if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Wire peer worker did not stop");
        }
        finished.get(5, TimeUnit.SECONDS);
    }
}
