package network;

import Gameplay.Gestores.Logicos.GestorScreen;
import com.principal.AntsArmageddon;
import entidades.personajes.Personaje;
import partida.ConfiguracionPartida;
import partida.online.GameScreenOnline;
import partida.online.LobbyScreen;
import screens.GameOverScreen;
import screens.MenuScreen;

public final class GameControllerImpl implements GameController {

    private final AntsArmageddon juego;
    private LobbyScreen lobbyScreen;
    private GameScreenOnline gameScreen;
    private int numJugador = -1;

    public GameControllerImpl(AntsArmageddon juego, LobbyScreen lobbyScreen) {
        this.juego = juego;
        this.lobbyScreen = lobbyScreen;
    }

    @Override
    public void connect(int numPlayer) {
        this.numJugador = numPlayer;
        System.out.println("[CLIENTE] Conectado como jugador #" + numPlayer);
        if (lobbyScreen != null)
            lobbyScreen.setJugadorNumero(numPlayer);
    }

    @Override
    public void start() {
        System.out.println("[CLIENTE] Ambos jugadores conectados. Esperando configuración final...");
    }

    @Override
    public void startGame(ConfiguracionPartida config) {
        System.out.println("[CLIENTE] Configuración recibida. Iniciando partida...");

        if (lobbyScreen != null) {
            lobbyScreen.iniciarPartida(config);
        } else if (gameScreen != null) {
            gameScreen.iniciarPartida(config);
        } else {
            System.err.println("[CLIENTE] No hay Lobby ni GameScreen activos para iniciar partida.");
        }
    }

    @Override
    public void backToMenu() {
        System.out.println("[CLIENTE] Desconectado del servidor. Volviendo al menú principal.");
        GestorScreen.setScreen(new MenuScreen(juego));
    }

    @Override
    public void updateTurno(int numJugadorActual, float tiempoRestante) {
        if (gameScreen == null || gameScreen.getGestorJuego() == null) return;

        gameScreen.getGestorJuego().getGestorTurno()
            .sincronizarTurno(numJugadorActual, tiempoRestante);

        System.out.println("[CLIENTE] Sincronizando turno: jugador=" + numJugadorActual + ", tiempoRestante=" + tiempoRestante);
    }

    @Override
    public void disparoRealizado(int numJugador, float angulo, float potencia) {
        if (gameScreen == null) return;

        System.out.println("[CLIENTE] Jugador " + numJugador + " disparó con ángulo=" + angulo + ", potencia=" + potencia);
        gameScreen.getGestorJuego().registrarDisparoRemoto(numJugador, angulo, potencia);
    }

    @Override
    public void impactoProyectil(float x, float y, int daño, boolean destruyeTerreno) {
        if (gameScreen == null) return;

        System.out.println("[CLIENTE] Impacto en (" + x + "," + y + ") daño=" + daño);
        gameScreen.getGestorJuego().procesarImpactoRemoto(x, y, daño, destruyeTerreno);
    }

    @Override
    public void personajeRecibeDanio(int numJugador, int idPersonaje, int daño, float fuerzaX, float fuerzaY) {
        if (gameScreen == null || gameScreen.getGestorJuego() == null) return;

        try {
            Personaje personaje = gameScreen.getGestorJuego().getJugadores().get(numJugador).getPersonajes().get(idPersonaje);
            personaje.recibirDanio(daño, fuerzaX, fuerzaY);
            System.out.println("[CLIENTE] Jugador " + numJugador + " personaje " + idPersonaje + " recibe " + daño + " de daño");
        } catch (Exception e) {
            System.err.println("[CLIENTE] Error aplicando daño remoto: " + e.getMessage());
        }
    }

    @Override
    public void personajeMuere(int numJugador, int idPersonaje) {
        if (gameScreen == null || gameScreen.getGestorJuego() == null) return;

        try {
            var personaje = gameScreen.getGestorJuego()
                .getJugadores().get(numJugador)
                .getPersonajes().get(idPersonaje);

            if (personaje.getVida() <= 0) {
                personaje.terminarTurno();
                System.out.println("[CLIENTE] Confirmada muerte personaje #" + idPersonaje + " del jugador " + numJugador);
            } else {
                personaje.recibirDanio(personaje.getVida(), 0, 0);
                System.out.println("[CLIENTE] Personaje #" + idPersonaje + " del jugador " + numJugador + " muerto por sincronización.");
            }

        } catch (Exception e) {
            System.err.println("[CLIENTE] Error aplicando muerte remota: " + e.getMessage());
        }
    }


    @Override
    public void endGame(int ganador) {
        System.out.println("[CLIENTE] Fin de partida. Ganador: Jugador " + ganador);
        GestorScreen.setScreen(new GameOverScreen(juego, "¡Jugador " + ganador + " gana!"));
    }

    public void setGameScreen(GameScreenOnline gameScreen) {
        this.gameScreen = gameScreen;
        this.lobbyScreen = null;
    }

    public int getNumJugador() {
        return numJugador;
    }

    public GameScreenOnline getGameScreen() {
        return gameScreen;
    }
}
