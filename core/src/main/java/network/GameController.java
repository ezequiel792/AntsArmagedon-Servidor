package network;

import partida.ConfiguracionPartida;

public interface GameController {

    void connect(int numJugador);
    void start();
    void startGame(ConfiguracionPartida config);
    void backToMenu();

    void updateTurno(int numJugadorActual, float tiempoRestante);
    void disparoRealizado(int numJugador, float angulo, float potencia);
    void impactoProyectil(float x, float y, int daño, boolean destruyeTerreno);
    void personajeRecibeDanio(int numJugador, int idPersonaje, int daño, float fuerzaX, float fuerzaY);
    void personajeMuere(int numJugador, int idPersonaje);
    void endGame(int ganador);
}
