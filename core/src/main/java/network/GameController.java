package network;

import partida.ConfiguracionPartidaServidor;

public interface GameController {

    void mover(int numJugador, float direccion);
    void saltar(int numJugador);
    void apuntar(int numJugador, int direccion);
    void disparar(int numJugador, float angulo, float potencia);
    void cambiarMovimiento(int numJugador, int indiceMovimiento);

    void iniciarPartida(ConfiguracionPartidaServidor config);
    void cambiarTurno();
    void registrarImpacto(float x, float y, int danio, boolean destruye);
    void generarPowerUp(float x, float y);
    void finalizarPartida(String mensaje);
}


/*
Game controller define en sus metodos las acciones que pueden realizar los clientes y eventos que pueden pasar en la
partida. Intermedia entre el cliente y el servidor. El flujo es el siguiente:

1- El jugador hace una accion y manda un mensaje: clientThread.sendMessage("Move:1");
2- El ServerThread recibe:  "Move:1"
3- Se fija en los casos y ejecuta el correspondiente, en este caso el de mover:

            case "Move":
                if (parts.length > 1)
                    gameController.mover(client.getNum(), Float.parseFloat(parts[1]));
                break;

4- Ejecuta esta funcion: gameController.mover(client.getNum(), Float.parseFloat(parts[1]));
En la implementacion del game screen, lo cual llama a este metodo:

    @Override
    public void mover(int numPlayer, float dir) {
        gestorJuego.moverPersonaje(numPlayer, dir);
    }

5- Ejecuta la funcion moverPersonaje del gestor juego:

    public void moverPersonaje(int idJugador, float dir) {
        Jugador jugador = jugadores.get(idJugador - 1);
        Personaje p = jugador.getPersonajeActivo();
        if (p != null && p.getActivo()) {
            p.mover(dir, Gdx.graphics.getDeltaTime());
        }
    }

6- La accion se ejecuta.

 */
