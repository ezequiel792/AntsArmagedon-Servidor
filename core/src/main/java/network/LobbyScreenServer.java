package network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.principal.AntsArmageddon;
import partida.ConfiguracionPartidaServidor;
import partida.online.GameScreenServidor;
import screens.ScreenMenus;

public class LobbyScreenServer extends ScreenMenus {

    private Label lblEstado;
    private ServerThread serverThread;
    private GameControllerEventos controladorEventos;
    private boolean partidaIniciada = false;

    public LobbyScreenServer(AntsArmageddon juego) {
        super(juego);
    }

    @Override
    protected void construirUI() {
        Label.LabelStyle estiloTexto = new Label.LabelStyle();
        estiloTexto.font = new BitmapFont();
        estiloTexto.fontColor = Color.WHITE;

        lblEstado = new Label("Esperando jugadores para iniciar la partida...", estiloTexto);
        lblEstado.setAlignment(Align.center);
        lblEstado.setWrap(true);

        Table tabla = new Table();
        tabla.setFillParent(true);
        tabla.center();
        tabla.add(lblEstado).width(600f).pad(20f).center();

        escenario.addActor(tabla);
    }

    @Override
    public void show() {
        super.show();
        iniciarServidor();
    }

    private void iniciarServidor() {
        serverThread = new ServerThread();
        controladorEventos = new GameControllerEventos(serverThread);
        serverThread.setGameController(controladorEventos);

        serverThread.start();

        actualizarTexto("[SERVIDOR] Esperando conexiones de jugadores...");
        System.out.println("[SERVIDOR] Lobby iniciado correctamente.");
    }

    public void actualizarTexto(String texto) {
        Gdx.app.postRunnable(() -> lblEstado.setText(texto));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        super.render(delta);

        if (!partidaIniciada && serverThread.getConfiguracionFinal() != null) {
            partidaIniciada = true;
            System.out.println("[SERVIDOR] Ambos jugadores listos. Iniciando partida...");
        }
    }


    private void iniciarPartida(ConfiguracionPartidaServidor configFinal) {
        Gdx.app.postRunnable(() -> {
            System.out.println("[SERVIDOR] Ambos jugadores listos. Iniciando partida...");
            juego.setScreen(new GameScreenServidor(juego, configFinal, serverThread, controladorEventos));
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        if (serverThread != null) {
            serverThread.terminar();
        }
    }
}
