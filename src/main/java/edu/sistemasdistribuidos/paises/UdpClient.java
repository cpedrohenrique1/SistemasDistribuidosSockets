package edu.sistemasdistribuidos.paises;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * UdpClient
 *
 * - Envia "JOIN" ao iniciar para ser registrado no servidor.
 * - Lê o console do usuário: "desisto" para sair, qualquer outra linha vira "GUESS:<texto>".
 * - Possui thread que escuta mensagens do servidor e imprime no console.
 */
public class UdpClient {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        new UdpClient().run();
    }

    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(0);
            InetAddress serverAddr = InetAddress.getByName(SERVER_HOST);

            // Listener thread
            Thread listener = new Thread(() -> {
                byte[] buf = new byte[BUFFER_SIZE];
                DatagramPacket pack = new DatagramPacket(buf, buf.length);
                while (true) {
                    try {
                        socket.receive(pack);
                        String msg = new String(pack.getData(), 0, pack.getLength(), StandardCharsets.UTF_8);
                        System.out.println(msg);
                    } catch (IOException e) {
                        System.err.println("[CLIENT] Receive error: " + e.getMessage());
                        break;
                    }
                }
            });
            listener.setDaemon(true);
            listener.start();

            // Send JOIN
            send(socket, "JOIN", serverAddr, SERVER_PORT);
            System.out.println("[CLIENT] Sent JOIN. Type your guesses (or 'desisto' to quit).");

            Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
            while (true) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("desisto")) {
                    send(socket, "DESISTO", serverAddr, SERVER_PORT);
                    System.out.println("[CLIENT] You gave up. Exiting.");
                    break;
                } else {
                    send(socket, "GUESS:" + line, serverAddr, SERVER_PORT);
                }
            }

        } catch (SocketException se) {
            System.err.println("[CLIENT] Socket error: " + se.getMessage());
        } catch (UnknownHostException ue) {
            System.err.println("[CLIENT] Unknown host: " + ue.getMessage());
        }
    }

    private void send(DatagramSocket socket, String message, InetAddress host, int port) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket dp = new DatagramPacket(data, data.length, host, port);
            socket.send(dp);
        } catch (IOException e) {
            System.err.println("[CLIENT] Error sending: " + e.getMessage());
        }
    }
}
