package partida.offline;

import Fisicas.Borde;
import Fisicas.Mapa;
import Gameplay.Gestores.GestorRutas;
import Gameplay.Gestores.Logicos.*;
import Gameplay.Gestores.Visuales.GestorAssets;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.principal.AntsArmageddon;
import com.principal.Jugador;
import entradas.ControlesJugador;
import hud.Hud;
import partida.ConfiguracionPartida;
import partida.FabricaPartida;
import screens.PauseScreen;
import utils.Constantes;
import utils.RecursosGlobales;
import entidades.personajes.Personaje;
import entidades.proyectiles.Proyectil;

import java.util.ArrayList;
import java.util.List;

public final class GameScreenOffline implements Screen {

    private final AntsArmageddon juego;
    private final ConfiguracionPartida configuracion;

    private Stage escenario;
    private Hud hud;
    private Sprite spriteMapa;
    private Mapa mapa;

    private GestorJuegoOffline gestorJuego;
    private List<ControlesJugador> controles = new ArrayList<>();
    private int turnoAnterior = -1;
    private boolean inicializado = false;

    public GameScreenOffline(AntsArmageddon juego, ConfiguracionPartida configuracion) {
        this.juego = juego;
        this.configuracion = configuracion;
    }

    @Override
    public void show() {
        if (!inicializado) {
            inicializarPartida();
            inicializado = true;
        }

        System.out.println("[OFFLINE] Partida iniciada en modo local.");

        if (turnoAnterior >= 0 && turnoAnterior < controles.size()) {
            controles.get(turnoAnterior).reset();
            Gdx.input.setInputProcessor(controles.get(turnoAnterior));
        }
    }

    private void inicializarPartida() {
        // === UI y escenario ===
        FitViewport viewport = new FitViewport(Constantes.RESOLUCION_ANCHO, Constantes.RESOLUCION_ALTO);
        escenario = new Stage(viewport);
        hud = new Hud();

        // === Mapa ===
        String mapaPath = switch (configuracion.getIndiceMapa()) {
            case 0 -> GestorRutas.MAPA_1;
            case 1 -> GestorRutas.MAPA_2;
            case 2 -> GestorRutas.MAPA_3;
            case 3 -> GestorRutas.MAPA_4;
            case 4 -> GestorRutas.MAPA_5;
            case 5 -> GestorRutas.MAPA_6;
            default -> GestorRutas.MAPA_1;
        };

        spriteMapa = new Sprite(GestorAssets.get(GestorRutas.FONDO_JUEGO, Texture.class));

        mapa = new Mapa(mapaPath);
        gestorJuego = FabricaPartida.crearGestorPartidaOffline(configuracion, mapa);

        Borde borde = new Borde(gestorJuego.getGestorColisiones());

        // === Configurar controles ===
        for (Jugador jugador : gestorJuego.getJugadores()) {
            ControlesJugador control = new ControlesJugador();
            jugador.setControlesJugador(control);
            controles.add(control);
        }

        // === Configurar entrada inicial ===
        int turnoInicial = gestorJuego.getTurnoActual();
        Gdx.input.setInputProcessor(controles.get(turnoInicial));
        turnoAnterior = turnoInicial;
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            GestorScreen.setScreen(new PauseScreen(juego, this));
            return;
        }

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
        gestorJuego.dispose();

        for (Jugador j : gestorJuego.getJugadores()) {
            j.getPersonajes().forEach(Personaje::dispose);
        }
    }
}
