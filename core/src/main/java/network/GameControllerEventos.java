package network;

import com.principal.Jugador;
import network.paquetes.entidades.PaqueteImpacto;
import network.paquetes.entidades.PaquetePowerUp;
import network.paquetes.partida.PaqueteCambioTurno;
import network.paquetes.partida.PaqueteFinPartida;
import network.paquetes.personaje.*;
import network.paquetes.utilidad.PaqueteMensaje;
import partida.ConfiguracionPartidaServidor;
import partida.online.GestorJuegoServidor;

public final class GameControllerEventos implements GameController {

    private final ServerThread serverThread;
    private GestorJuegoServidor gestorJuego;

    public GameControllerEventos(ServerThread serverThread) {
        this.serverThread = serverThread;
    }

    @Override
    public void mover(int numJugador, float direccion) {
        System.out.println("[SERVIDOR] Mover jugador " + numJugador + " dir=" + direccion);
        if (gestorJuego != null)
            gestorJuego.moverPersonaje(numJugador, direccion);

        serverThread.enviarATodos(new PaqueteMover(numJugador, direccion));
    }

    @Override
    public void saltar(int numJugador) {
        if (gestorJuego != null)
            gestorJuego.saltarPersonaje(numJugador);

        PaqueteSaltar p = new PaqueteSaltar(numJugador);
        serverThread.enviarATodos(p);
    }

    @Override
    public void apuntar(int numJugador, int direccion) {
        if (gestorJuego != null)
            gestorJuego.apuntarPersonaje(numJugador, direccion);

        PaqueteApuntar p = new PaqueteApuntar(numJugador, direccion);
        serverThread.enviarATodos(p);
    }

    @Override
    public void disparar(int numJugador, float angulo, float potencia) {
        if (gestorJuego == null) return;
        gestorJuego.dispararPersonaje(numJugador, angulo, potencia);

        PaqueteDisparar p = new PaqueteDisparar(numJugador, angulo, potencia);
        serverThread.enviarATodos(p);
    }

    @Override
    public void cambiarMovimiento(int numJugador, int indiceMovimiento) {
        if (gestorJuego != null)
            gestorJuego.cambiarMovimientoPersonaje(numJugador, indiceMovimiento);

        PaqueteCambioMovimiento p = new PaqueteCambioMovimiento(numJugador, indiceMovimiento);
        serverThread.enviarATodos(p);
    }

    @Override
    public void iniciarPartida(ConfiguracionPartidaServidor config) {
        System.out.println("[SERVIDOR] iniciarPartida() llamado (ya inicializado desde ServerThread).");
    }

    @Override
    public void cambiarTurno() {
        if (gestorJuego == null) return;

        gestorJuego.cambiarTurno();
        int turno = gestorJuego.getGestorTurno().getTurnoActual();
        float tiempo = gestorJuego.getGestorTurno().getTiempoActual();

        Jugador jugadorActivo = gestorJuego.getGestorTurno().getJugadorActivo();
        int jugadorId = jugadorActivo != null ? jugadorActivo.getIdJugador() : 0;
        int personajeIndex = jugadorActivo != null ? jugadorActivo.getIndicePersonajeActivo() : 0;

        PaqueteCambioTurno p = new PaqueteCambioTurno(turno, tiempo, jugadorId, personajeIndex);
        serverThread.enviarATodos(p);
    }

    @Override
    public void registrarImpacto(float x, float y, int danio, boolean destruye) {
        if (gestorJuego == null) return;
        gestorJuego.procesarImpactoRemoto(x, y, danio, destruye);

        PaqueteImpacto p = new PaqueteImpacto(x, y, danio, destruye);
        serverThread.enviarATodos(p);
    }

    @Override
    public void generarPowerUp(float x, float y) {
        PaquetePowerUp p = new PaquetePowerUp(x, y);
        serverThread.enviarATodos(p);
    }

    @Override
    public void finalizarPartida(String mensaje) {
        PaqueteFinPartida p = new PaqueteFinPartida(mensaje);
        serverThread.enviarATodos(p);
        System.out.println("[SERVIDOR] " + mensaje);
    }

    public void enviarATodos(String mensaje) {
        PaqueteMensaje p = new PaqueteMensaje(mensaje);
        serverThread.enviarATodos(p);
    }

    public void enviarAJugador(int numJugador, String mensaje) {
        System.out.println("[SERVIDOR → Jugador" + numJugador + "] " + mensaje);
    }

    public void setGestorJuego(GestorJuegoServidor gestor) { this.gestorJuego = gestor; }
    public GestorJuegoServidor getGestorJuego() { return gestorJuego; }
    public ServerThread getServerThread() { return this.serverThread; }
}
