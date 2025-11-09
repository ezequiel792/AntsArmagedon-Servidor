package network;

import Fisicas.Mapa;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.principal.AntsArmageddon;
import com.principal.Jugador;
import network.paquetes.partida.PaqueteCambioTurno;
import network.paquetes.partida.PaqueteConfiguracion;
import network.paquetes.partida.PaqueteEstadoPartida;
import network.paquetes.partida.PaqueteInicioPartida;
import network.paquetes.personaje.PaqueteApuntar;
import network.paquetes.personaje.PaqueteCambioMovimiento;
import network.paquetes.personaje.PaqueteDisparar;
import network.paquetes.personaje.PaqueteMover;
import network.paquetes.utilidad.DatosJuego;
import network.paquetes.utilidad.PaqueteMensaje;
import partida.ConfiguracionPartidaServidor;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import network.paquetes.*;
import partida.FabricaPartidaServidor;
import partida.online.GameScreenServidor;
import partida.online.GestorJuegoServidor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerThread extends Thread {

    private DatagramSocket socket;
    private final int PUERTO = 5555;
    private boolean finalizar = false;

    private static final int MAX_CLIENTES = 2;
    private final ArrayList<Client> clientes = new ArrayList<>();

    private GameControllerEventos controlador;
    private GestorJuegoServidor gestorJuego;

    private ConfiguracionPartidaServidor configJ1;
    private ConfiguracionPartidaServidor configJ2;
    private ConfiguracionPartidaServidor configuracionFinal;
    private List<Vector2> spawnsPartida;

    private final AtomicInteger nextId = new AtomicInteger(1);

    public int generarIdEntidad() {
        return nextId.getAndIncrement();
    }

    public ServerThread() {
        try {
            socket = new DatagramSocket(PUERTO);
            socket.setBroadcast(true);
            socket.setSoTimeout(10);
            System.out.println("[SERVIDOR] Iniciado en puerto UDP " + PUERTO);
        } catch (SocketException e) {
            System.err.println("[SERVIDOR] Error iniciando servidor UDP: " + e.getMessage());
        }
    }

    public void setGameController(GameControllerEventos controlador) {
        this.controlador = controlador;
    }

    @Override
    public void run() {
        long ultimoTick = System.currentTimeMillis();

        while (!finalizar) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
                socket.receive(packet);
                procesarPaquete(packet);
            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                if (!finalizar)
                    System.err.println("[SERVIDOR] Error recibiendo paquete: " + e.getMessage());
            }

            if (gestorJuego != null) {
                long ahora = System.currentTimeMillis();
                if (ahora - ultimoTick >= 50) {
                    gestorJuego.actualizar(0.05f);
                    enviarEstadoPartida();
                    ultimoTick = ahora;
                }
            }
        }
        System.out.println("[SERVIDOR] Hilo detenido.");
    }

    private void procesarPaquete(DatagramPacket packet) {
        try {
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            PaqueteRed paquete = PaqueteRed.deserializar(data);
            if (paquete == null) return;

            int index = buscarCliente(packet);
            switch (paquete.getTipo()) {

                case CONEXION -> manejarConexion(packet, index);

                case CONFIGURACION -> {
                    if (index != -1)
                        manejarConfiguracion(clientes.get(index), (PaqueteConfiguracion) paquete);
                }

                case MOVER -> {
                    if (index != -1) {
                        PaqueteMover p = (PaqueteMover) paquete;
                        controlador.mover(clientes.get(index).getNum(), p.direccion);
                    }
                }

                case SALTAR -> {
                    if (index != -1)
                        controlador.saltar(clientes.get(index).getNum());
                }

                case APUNTAR -> {
                    if (index != -1) {
                        PaqueteApuntar p = (PaqueteApuntar) paquete;
                        controlador.apuntar(clientes.get(index).getNum(), p.direccion);
                    }
                }

                case DISPARO -> {
                    if (index != -1) {
                        PaqueteDisparar p = (PaqueteDisparar) paquete;
                        controlador.disparar(clientes.get(index).getNum(), p.angulo, p.potencia);
                    }
                }

                case CAMBIAR_MOVIMIENTO -> {
                    if (index != -1) {
                        PaqueteCambioMovimiento p = (PaqueteCambioMovimiento) paquete;
                        controlador.cambiarMovimiento(clientes.get(index).getNum(), p.indiceMovimiento);
                    }
                }

                case CAMBIO_TURNO -> controlador.cambiarTurno();

                default -> System.out.println("[SERVIDOR] Paquete no manejado: " + paquete.getTipo());
            }

        } catch (Exception e) {
            System.err.println("[SERVIDOR] Error procesando paquete: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void manejarConexion(DatagramPacket packet, int index) {
        InetAddress ip = packet.getAddress();
        int port = packet.getPort();

        if (index != -1) {
            enviarPaquete(new PaqueteMensaje("YA_CONECTADO"), ip, port);
            return;
        }

        if (clientes.size() >= MAX_CLIENTES) {
            enviarPaquete(new PaqueteMensaje("SERVIDOR_LLENO"), ip, port);
            return;
        }

        Client nuevo = new Client(clientes.size(), ip, port);
        clientes.add(nuevo);

        enviarPaquete(new PaqueteConexion(nuevo.getNum(), true), ip, port);
        System.out.println("[SERVIDOR] Cliente #" + nuevo.getNum() + " conectado (" + ip + ":" + port + ")");

        if (clientes.size() == MAX_CLIENTES) {
            enviarATodos(new PaqueteMensaje("LISTO"));
            System.out.println("[SERVIDOR] Ambos jugadores conectados. Esperando configuraciones...");
        }
    }

    private void manejarConfiguracion(Client client, PaqueteConfiguracion paquete) {
        ConfiguracionPartidaServidor config = ConfiguracionPartidaServidor.desdeString(paquete.configString);

        if (client.getNum() == 0) {
            configJ1 = config;
            System.out.println("[SERVIDOR] Configuración recibida de Jugador 1.");
        } else {
            configJ2 = config;
            System.out.println("[SERVIDOR] Configuración recibida de Jugador 2.");
        }

        if (configJ1 != null && configJ2 != null) {
            configuracionFinal = fusionarConfiguraciones(configJ1, configJ2);
            System.out.println("[SERVIDOR] CONFIG FINAL: " + configuracionFinal.toNetworkString());
            Gdx.app.postRunnable(() -> iniciarPartida(configuracionFinal));
        }
    }

    private ConfiguracionPartidaServidor fusionarConfiguraciones(
        ConfiguracionPartidaServidor c1, ConfiguracionPartidaServidor c2) {

        ConfiguracionPartidaServidor finalC = new ConfiguracionPartidaServidor();

        finalC.setMapa(c1.getIndiceMapa());
        finalC.setTiempoTurnoPorIndice(buscarIndiceTiempo(c1.getTiempoTurno()));
        finalC.setFrecuenciaPowerUpsPorIndice(buscarIndiceFrecuencia(c1.getFrecuenciaPowerUps()));
        finalC.getEquipoJugador1().addAll(c1.getEquipoJugador1());
        finalC.getEquipoJugador2().addAll(c2.getEquipoJugador1());

        return finalC;
    }

    private void iniciarPartida(ConfiguracionPartidaServidor configFinal) {
        try {
            Mapa mapa = new Mapa(configFinal.getRutaMapa());

            gestorJuego = FabricaPartidaServidor.crearGestorPartidaServidor(configFinal, mapa, null, this);
            spawnsPartida = gestorJuego.getGestorSpawn().getSpawnsIniciales();
            controlador.setGestorJuego(gestorJuego);

            Gdx.app.postRunnable(() -> {
                AntsArmageddon juego = (AntsArmageddon) Gdx.app.getApplicationListener();
                juego.setScreen(new GameScreenServidor(juego, configFinal, this, controlador));
            });

            for (Client c : clientes) {
                int jugadorId = c.getNum();
                PaqueteInicioPartida p = new PaqueteInicioPartida(jugadorId, configFinal.toNetworkString(), spawnsPartida);
                enviarPaquete(p, c.getIp(), c.getPort());
            }

            int turno = gestorJuego.getGestorTurno().getTurnoActual();
            Jugador jugador = gestorJuego.getGestorTurno().getJugadorActivo();
            int jugadorId = jugador.getIdJugador();
            int personajeIndex = jugador.getIndicePersonajeActivo();
            float tiempo = gestorJuego.getGestorTurno().getTiempoActual();
            enviarATodos(new PaqueteCambioTurno(turno, tiempo, jugadorId, personajeIndex));

            System.out.println("[SERVIDOR] Partida iniciada correctamente.");

        } catch (Exception e) {
            System.err.println("[SERVIDOR] Error iniciando partida: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void enviarEstadoPartida() {
        if (gestorJuego == null) return;

        List<DatosJuego.EntidadDTO> entidades = gestorJuego.obtenerEntidadesDTO();
        List<DatosJuego.ProyectilDTO> proyectiles = gestorJuego.obtenerProyectilesDTO();

        int jugadorEnTurno = gestorJuego.getGestorTurno().getJugadorActivo().getIdJugador();
        float tiempoRestante = gestorJuego.getGestorTurno().getTiempoActual();
        boolean enTransicion = gestorJuego.getGestorTurno().isEnTransicion();
        int personajeIndex = gestorJuego.getGestorTurno().getJugadorActivo().getIndicePersonajeActivo();

        PaqueteEstadoPartida p = new PaqueteEstadoPartida(
            entidades,
            proyectiles,
            jugadorEnTurno,
            tiempoRestante,
            enTransicion,
            personajeIndex
        );

        enviarATodos(p);
    }

    public void enviarPaquete(PaqueteRed paquete, InetAddress ip, int puerto) {
        try {
            byte[] data = paquete.serializar();
            socket.send(new DatagramPacket(data, data.length, ip, puerto));
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Error al enviar paquete: " + e.getMessage());
        }
    }

    public void enviarATodos(PaqueteRed paquete) {
        for (Client c : clientes)
            enviarPaquete(paquete, c.getIp(), c.getPort());
    }

    private int buscarCliente(DatagramPacket packet) {
        String id = packet.getAddress().toString() + ":" + packet.getPort();
        for (int i = 0; i < clientes.size(); i++)
            if (clientes.get(i).getId().equals(id)) return i;
        return -1;
    }

    public void terminar() {
        finalizar = true;
        if (socket != null && !socket.isClosed())
            socket.close();
        interrupt();
    }

    private int buscarIndiceTiempo(int tiempo) {
        for (int i = 0; i < ConfiguracionPartidaServidor.OPCIONES_TIEMPO_TURNO.length; i++)
            if (ConfiguracionPartidaServidor.OPCIONES_TIEMPO_TURNO[i] == tiempo) return i;
        return 0;
    }

    private int buscarIndiceFrecuencia(int freq) {
        for (int i = 0; i < ConfiguracionPartidaServidor.OPCIONES_FRECUENCIA_PU.length; i++)
            if (ConfiguracionPartidaServidor.OPCIONES_FRECUENCIA_PU[i] == freq) return i;
        return 0;
    }

    public List<Client> getClientes() { return clientes; }
    public ConfiguracionPartidaServidor getConfiguracionFinal() { return configuracionFinal; }
    public void setConfiguracionFinal(ConfiguracionPartidaServidor c) { configuracionFinal = c; }
    public List<Vector2> getSpawnsPartida() { return spawnsPartida; }
    public void setSpawnsPartida(List<Vector2> s) { spawnsPartida = s; }
    public GestorJuegoServidor getGestorJuego() { return gestorJuego; }
}

/*
Conceptos de redes:

1-UDP (User Datagram Protocol): Es un protocolo de comunicacion que se usa para enviar datos entre computadoras
sin conexion previa. Es muy rapida pero no tiene garantia, osea puede perder paquetes. Para nuestro juego
usamos este, ya que necesitamos velocidad antes que consistencia por las caracteristicas de nuestro juego.
La alternativa seria TCP, pero ese protocolo se usa mas cuando preferis consistencia a velocidad, por ejemplo
en un sistema de mensajeria, no te importa mucha la velocidad como si la consistencia, ya que si un mensaje tarda
mas o menos en enviarse no es tan grave como tener un mensaje que le falta una letra o que tiene una cambiada, esto
arruina el mensaje y el proposito del sistema.

2-DatagramSocket: Canal de comunicacion UDP del servidor. Permite enviar y recibir datagramas.
3-serverPort: Es el puerto en el que el servidor recibe mensajes, esta fijo porque estamos en una red local.

4-Extends Thread: La clase extiende de Thread, osea, representa un hilo de ejecucion independiente.
5-Thread: Un Thread o Hilo, es una linea de ejecucion paralela. Cuando nosotros creamos un pograma de java, y hacemos
    public static void main(String[] args) {, estamos haciendo un hilo, este seria el hilo principal de nuestro
programa, si despues hacemos una clase que extiende de Thread, estamos creando un hilo aparte que se ejecuta
en paralelo al original. En este caso lo utilizamos para recibir mensajes de red de los clientes.

Para que se vea, este es el metodo que ejecuta al juego:

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

Y nuestro hilo de ServerThread se ejecuta asi en el game screen:
        serverThread = new ServerThread(this);
        serverThread.start();

6-Constructor: El constructor de la clase, la cual recibe el GameController para poder ejecutar sus metodos.
E inicializamos el datagram socket en el puerto 5555, que es uno que esta libre.

7-Run: Es un bucle infinito que escucha red, y procesa los mensajes que llegan al servidor. La rutina se ejecuta
con el serverThread.start();
El metodo se corre mientras end sea falso, lo cual dejara el hilo activo hasta que el programa lo cierre.
            DatagramPacket packet = new DatagramPacket(new byte[1024], 1024); Es el contenedor del UDP, que almacenara
los datos recibidos. Y:

             socket.receive(packet);

Hace recibir el paquete en el socket de UDP. Este paquete tiene datos adentro, que se pueden obtener y seran
utiles obtener su ip o informacion: String message = (new String(packet.getData())).trim();

             processMessage(packet);

LLama al metodo que procesa, obtienee y traduce en una accion el mensaje.

8- Ejemplo de paquete:

Cliente manda un mensaje: clientThread.sendMessage("Move:1");

El servidor recibe un paquete UDP que contiene datos, por ejemplo:

DatagramPacket {
    data = "Move:1"
    length = 6
    address = /192.168.0.23
    port = 60532
}
 */

/*Arreglar despuues, para que la partida solo empiece cuando ambos jugadores enviaron su  config.*/
