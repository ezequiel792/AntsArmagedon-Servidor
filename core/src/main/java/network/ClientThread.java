package network;

import partida.ConfiguracionPartida;
import java.io.IOException;
import java.net.*;

public class ClientThread extends Thread {

    private DatagramSocket socket;
    private int serverPort = 5555;
    private String ipServerStr = "127.0.0.1";
    private InetAddress ipServer;
    private volatile boolean end = false;

    private final GameController gameController;

    public ClientThread(GameController gameController) {
        this.gameController = gameController;
        try {
            ipServer = InetAddress.getByName(ipServerStr);
            socket = new DatagramSocket();
            socket.setSoTimeout(0);
        } catch (SocketException | UnknownHostException e) {
            System.err.println("[CLIENTE] Error iniciando socket: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        while (!end) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
                socket.receive(packet);
                processMessage(packet);
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                if (!end)
                    System.err.println("[CLIENTE] Error al recibir: " + e.getMessage());
            }
        }
        System.out.println("[CLIENTE] Hilo finalizado.");
    }

    private void processMessage(DatagramPacket packet) {
        String msg = new String(packet.getData(), 0, packet.getLength()).trim();
        System.out.println("[CLIENTE] Mensaje recibido: " + msg);

        try {
            String[] parts = msg.split(":");
            String cmd = parts[0];

            switch (cmd) {

                case "Connected" -> {
                    int numJugador = Integer.parseInt(parts[1]);
                    gameController.connect(numJugador);
                }
                case "Start" -> {
                    System.out.println("[CLIENTE] Recibido Start → enviando configuración automática...");

                    ConfiguracionPartida config = new ConfiguracionPartida();
                    config.normalizarEquipos();

                    sendMessage("CONFIG:" + config.toNetworkString());
                }


                case "StartGame" -> {
                    ConfiguracionPartida config = null;
                    if (parts.length > 1) {
                        try {
                            config = ConfiguracionPartida.desdeString(parts[1]);
                        } catch (Exception e) {
                            System.err.println("[CLIENTE] Error parseando config: " + e.getMessage());
                        }
                    }
                    gameController.startGame(config);
                }

                case "Disconnect" -> gameController.backToMenu();

                case "UpdateTurno" -> {
                    int numJugador = Integer.parseInt(parts[1]);
                    float tiempoRestante = Float.parseFloat(parts[2]);
                    gameController.updateTurno(numJugador, tiempoRestante);
                }

                case "Disparo" -> {
                    int numJugador = Integer.parseInt(parts[1]);
                    float angulo = Float.parseFloat(parts[2]);
                    float potencia = Float.parseFloat(parts[3]);
                    gameController.disparoRealizado(numJugador, angulo, potencia);
                }

                case "Impacto" -> {
                    float x = Float.parseFloat(parts[1]);
                    float y = Float.parseFloat(parts[2]);
                    int daño = Integer.parseInt(parts[3]);
                    boolean destruye = Boolean.parseBoolean(parts[4]);
                    gameController.impactoProyectil(x, y, daño, destruye);
                }

                case "Danio" -> {
                    int numJugador = Integer.parseInt(parts[1]);
                    int idPersonaje = Integer.parseInt(parts[2]);
                    int daño = Integer.parseInt(parts[3]);
                    float fuerzaX = Float.parseFloat(parts[4]);
                    float fuerzaY = Float.parseFloat(parts[5]);
                    gameController.personajeRecibeDanio(numJugador, idPersonaje, daño, fuerzaX, fuerzaY);
                }

                case "Muerte" -> {
                    int numJugador = Integer.parseInt(parts[1]);
                    int idPersonaje = Integer.parseInt(parts[2]);
                    gameController.personajeMuere(numJugador, idPersonaje);
                }

                case "EndGame" -> {
                    int ganador = Integer.parseInt(parts[1]);
                    gameController.endGame(ganador);
                }

                default -> System.out.println("[CLIENTE] Comando desconocido: " + msg);
            }

        } catch (Exception e) {
            System.err.println("[CLIENTE] Error procesando mensaje: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (socket == null || socket.isClosed()) return;
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, ipServer, serverPort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[CLIENTE] Error enviando mensaje: " + e.getMessage());
        }
    }

    public void terminate() {
        end = true;
        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (Exception ignored) {}
        interrupt();
    }
}
