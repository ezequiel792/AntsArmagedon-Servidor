package partida.online;

import Fisicas.Mapa;
import Gameplay.Gestores.GestorRutas;
import Gameplay.Gestores.Visuales.GestorAssets;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.principal.AntsArmageddon;
import com.principal.Jugador;
import entidades.personajes.Personaje;
import entidades.proyectiles.Proyectil;
import hud.Hud;
import network.GameControllerEventos;
import network.ServerThread;
import partida.ConfiguracionPartidaServidor;
import partida.FabricaPartidaServidor;
import java.util.List;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import utils.Constantes;
import utils.RecursosGlobales;

public final class GameScreenServidor implements Screen {

    private final AntsArmageddon juego;
    private final ConfiguracionPartidaServidor configuracion;
    private final ServerThread serverThread;
    private final GameControllerEventos controlador;

    private Stage escenario;
    private Hud hud;
    private Sprite spriteMapa;
    private Mapa mapa;
    private GestorJuegoServidor gestorJuego;

    private boolean inicializado = false;

    public GameScreenServidor(
        AntsArmageddon juego,
        ConfiguracionPartidaServidor configuracion,
        ServerThread serverThread,
        GameControllerEventos controlador
    ) {
        this.juego = juego;
        this.configuracion = configuracion;
        this.serverThread = serverThread;
        this.controlador = controlador;
    }

    @Override
    public void show() {
        if (!inicializado) {
            inicializarVisual();
            inicializarPartida();
            inicializado = true;
        }
    }

    private void inicializarVisual() {
        FitViewport viewport = new FitViewport(Constantes.RESOLUCION_ANCHO, Constantes.RESOLUCION_ALTO);
        escenario = new Stage(viewport);
        hud = new Hud();

        spriteMapa = new Sprite(GestorAssets.get(GestorRutas.FONDO_JUEGO, Texture.class));
        mapa = new Mapa(configuracion.getRutaMapa());
    }

    private void inicializarPartida() {
        System.out.println("[SERVIDOR] Inicializando partida online...");

        List<Vector2> spawns = serverThread.getSpawnsPartida();
        if (spawns == null || spawns.isEmpty()) {
            throw new IllegalStateException("[SERVIDOR] ERROR: spawnsPartida vacío. Debe setearse ANTES de crear GameScreenServidor.");
        }

        gestorJuego = FabricaPartidaServidor.crearGestorPartidaServidor(
            configuracion,
            mapa,
            spawns,
            serverThread
        );

        controlador.setGestorJuego(gestorJuego);
        System.out.println("[SERVIDOR] Partida inicializada y sincronizada con los clientes.");

        System.out.println("[SERVIDOR] Estado actual de jugadores:");
        for (Jugador j : gestorJuego.getJugadores()) {
            j.imprimirEstadoDebug();
        }

    }

    @Override
    public void render(float delta) {
        if (gestorJuego == null) return;

        gestorJuego.actualizar(delta);

        Gdx.gl.glClearColor(0.7f, 0.7f, 0.7f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        Proyectil proyectil = gestorJuego.getGestorProyectiles().getUltimoProyectilActivo();
        Personaje activo = gestorJuego.getPersonajeActivo();

        if (proyectil != null) {
            RecursosGlobales.camaraJuego.seguirPosicion(proyectil.getX(), proyectil.getY());
        } else if (!gestorJuego.getGestorTurno().isEnTransicion() && activo != null) {
            RecursosGlobales.camaraJuego.seguirPersonaje(activo);
        }

        RecursosGlobales.camaraJuego.getCamera().update();
        RecursosGlobales.batch.setProjectionMatrix(RecursosGlobales.camaraJuego.getCamera().combined);

        RecursosGlobales.batch.begin();
        spriteMapa.draw(RecursosGlobales.batch);
        mapa.render();
        gestorJuego.renderEntidades(RecursosGlobales.batch);
        gestorJuego.renderPersonajes(hud);
        gestorJuego.renderProyectiles(RecursosGlobales.batch);
        hud.mostrarContador(gestorJuego.getTiempoActual(), RecursosGlobales.camaraJuego);
        if (activo != null) hud.mostrarAnimSelectorMovimientos(activo, RecursosGlobales.camaraJuego, delta);
        RecursosGlobales.batch.end();

        if (activo != null) hud.mostrarBarraCarga(activo);

        gestorJuego.renderDebug(RecursosGlobales.shapeRenderer, RecursosGlobales.camaraJuego);

        escenario.act(delta);
        escenario.draw();
    }

    @Override
    public void resize(int width, int height) {
        RecursosGlobales.camaraJuego.getViewport().update(width, height, true);
        escenario.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        escenario.dispose();
        hud.dispose();
        spriteMapa.getTexture().dispose();
        mapa.dispose();
        if (gestorJuego != null) gestorJuego.dispose();
        if (serverThread != null) serverThread.terminar();
    }
}
