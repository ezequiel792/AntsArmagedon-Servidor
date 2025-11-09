package hud;

import Gameplay.Gestores.GestorAudio;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import Gameplay.Gestores.Logicos.GestorScreen;

public final class EventosBoton {

    private EventosBoton() {}


    public static Runnable salirJuego() {
        return Gdx.app::exit;
    }

    public static Runnable irScreenAnterior() {
        return GestorScreen::irScreenAnterior;
    }

    /*public static Runnable reanudarJuego(AntsArmageddon juego, GameScreenOffline gameScreenOffline) {
        return () -> juego.setScreen(gameScreenOffline);
    }*/

    public static Runnable ajustarVolumenMusica(float nuevoVolumen) {
        return () -> GestorAudio.setVolumenMusica(nuevoVolumen);
    }

    public static Runnable ajustarVolumenSFX(float nuevoVolumen) {
        return () -> GestorAudio.setVolumenSFX(nuevoVolumen);
    }

    public static Runnable muteTotal(Slider sliderMusica, Slider sliderSFX) {
        return () -> {
            GestorAudio.setVolumenMusica(0);
            GestorAudio.setVolumenSFX(0);
            sliderMusica.setValue(0);
            sliderSFX.setValue(0);
        };
    }

    //Eventos de utilidad, para usar cuando se necesiten:

    public static Runnable descomponerAtlas() {
        return () -> utils.Utiles.descomponerAtlas("botones/botones.atlas", "atlasDescompuestos/");
    }

    public static Runnable descomponerSheet(String sheetPath, int cols, int rows, String outputFolder) {
        return () -> utils.Utiles.descomponerSheet(sheetPath, cols, rows, outputFolder);
    }
}
