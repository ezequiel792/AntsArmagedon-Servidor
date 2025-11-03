package partida.online;

import Fisicas.Mapa;
import Gameplay.Gestores.GestorRutas;
import Gameplay.Gestores.Visuales.GestorAssets;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.principal.AntsArmageddon;
import com.principal.Jugador;
import entidades.personajes.Personaje;
import entidades.proyectiles.Proyectil;
import entradas.ControlesJugador;
import hud.Hud;
import network.ClientThread;
import network.GameControllerImpl;
import partida.ConfiguracionPartida;
import partida.FabricaPartida;
import utils.Constantes;
import utils.RecursosGlobales;

import java.util.ArrayList;
import java.util.List;

public final class GameScreenOnline implements Screen {

    private final AntsArmageddon juego;
    private final ConfiguracionPartida configuracion;
    private final ClientThread clientThread;
    private final GameControllerImpl controller;

    private Stage escenario;
    private Hud hud;
    private Sprite spriteMapa;
    private Mapa mapa;

    private GestorJuegoOnline gestorJuego;
    private final List<ControlesJugador> controles = new ArrayList<>();
    private int turnoAnterior = -1;

    private boolean esperandoJugadores = true;

    public GameScreenOnline(
        AntsArmageddon juego,
        ConfiguracionPartida configuracion,
        ClientThread clientThread,
        GameControllerImpl controller
    ) {
        this.juego = juego;
        this.configuracion = configuracion;
        this.clientThread = clientThread;
        this.controller = controller;
    }

    @Override
    public void show() {
        controller.setGameScreen(this);
        inicializarVisual();
        inicializarPartida();
    }

    private void inicializarVisual() {
        FitViewport viewport = new FitViewport(Constantes.RESOLUCION_ANCHO, Constantes.RESOLUCION_ALTO);
        escenario = new Stage(viewport);
        hud = new Hud();

        String mapaPath = switch (configuracion.getIndiceMapa()) {
            case 1 -> GestorRutas.MAPA_2;
            case 2 -> GestorRutas.MAPA_3;
            case 3 -> GestorRutas.MAPA_4;
            case 4 -> GestorRutas.MAPA_5;
            case 5 -> GestorRutas.MAPA_6;
            default -> GestorRutas.MAPA_1;
        };

        spriteMapa = new Sprite(GestorAssets.get(GestorRutas.FONDO_JUEGO, Texture.class));
        mapa = new Mapa(mapaPath);
    }

    private void inicializarPartida() {
        gestorJuego = FabricaPartida.crearGestorPartidaOnline(configuracion, mapa);

        for (Jugador jugador : gestorJuego.getJugadores()) {
            ControlesJugador control = new ControlesJugador();
            jugador.setControlesJugador(control);
            controles.add(control);
        }

        int turnoInicial = gestorJuego.getTurnoActual();
        if (!controles.isEmpty())
            Gdx.input.setInputProcessor(controles.get(turnoInicial));

        turnoAnterior = turnoInicial;
        esperandoJugadores = false;

        System.out.println("[CLIENTE] Partida online inicializada correctamente.");
    }

    @Override
    public void render(float delta) {
        if (esperandoJugadores) return;

        gestorJuego.actualizar(delta, mapa);

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

        if (activo != null)
            hud.mostrarAnimSelectorMovimientos(activo, RecursosGlobales.camaraJuego, delta);

        RecursosGlobales.batch.end();

        if (activo != null)
            hud.mostrarBarraCarga(activo);

        gestorJuego.renderDebug(RecursosGlobales.shapeRenderer, RecursosGlobales.camaraJuego);

        escenario.act(delta);
        escenario.draw();

        procesarEntradaJugador(delta);
        actualizarTurno();
    }

    private void actualizarTurno() {
        int turnoActual = gestorJuego.getTurnoActual();
        if (turnoActual != turnoAnterior && turnoActual >= 0 && turnoActual < controles.size()) {
            controles.get(turnoAnterior).reset();
            Gdx.input.setInputProcessor(controles.get(turnoActual));
            turnoAnterior = turnoActual;
        }
    }

    private void procesarEntradaJugador(float delta) {
        ControlesJugador control = controles.get(gestorJuego.getTurnoActual());
        gestorJuego.procesarEntradaJugador(control, delta);
    }

    public void setJugadorNumero(int num) {
        System.out.println("[CLIENTE] Soy el jugador #" + num);
    }

    public GestorJuegoOnline getGestorJuego() {
        return gestorJuego;
    }

    public void iniciarPartida(ConfiguracionPartida config) {
        this.esperandoJugadores = false;
        this.configuracion.setDatosDesde(config);
        this.inicializarPartida();
        System.out.println("[CLIENTE] Partida iniciada con configuración del servidor.");
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
        if (clientThread != null) clientThread.terminate();
        escenario.dispose();
        hud.dispose();
        spriteMapa.getTexture().dispose();
        mapa.dispose();
        if (gestorJuego != null) gestorJuego.dispose();
    }
}
