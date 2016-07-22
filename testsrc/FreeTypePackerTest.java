import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.StringBuilder;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import darkyenus.resourcepacker.util.FreeTypePacker;

import java.io.File;

/**
 *
 */
public class FreeTypePackerTest {
    public static void main(String[] args){
        GdxNativesLoader.load();

        final FreeTypePacker packer = new FreeTypePacker("FontName",new FileHandle(new File("testresources/UISkin.pack/goudy-bookletter-1911.32.from glyphs.ignore.ttf")));
        final FreeTypePacker.FreeTypeFontParameter parameter = new FreeTypePacker.FreeTypeFontParameter();
        parameter.size = 100;
        parameter.borderWidth = 2;
        parameter.borderColor = Color.BLUE;
        parameter.borderStraight = false;
        parameter.kerning = true;
        parameter.shadowColor = Color.GREEN;
        parameter.shadowOffsetX = 3;
        parameter.shadowOffsetY = 8;

        final FileHandle outputFolder = new FileHandle(new File("target/FreeTypePackerTestResult"));
        packer.generate(parameter, outputFolder);


        final Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.disableAudio(true);
        config.setResizable(true);
        config.setTitle("Hello world");
        config.setWindowSizeLimits(50, 50, 5000, 5000);
        config.setDecorated(true);
        config.setWindowedMode(600, 500);
        config.setHdpiMode(Lwjgl3ApplicationConfiguration.HdpiMode.Logical);

        new Lwjgl3Application(new ApplicationAdapter() {

            BitmapFont font;
            BitmapFont defaultFont;
            SpriteBatch batch;
            ScreenViewport viewport;
            ShapeRenderer shapeRenderer;

            final StringBuilder text = new StringBuilder("Hello world");

            @Override
            public void create() {
                font = new BitmapFont(outputFolder.child("FontName.fnt"));
                defaultFont = new BitmapFont();
                batch = new SpriteBatch();
                viewport = new ScreenViewport();
                shapeRenderer = new ShapeRenderer();

                Gdx.input.setInputProcessor(new InputAdapter(){
                    @Override
                    public boolean keyTyped(char character) {
                        if(character == 8){
                            if(text.length() != 0) {
                                text.setLength(text.length() - 1);
                            }
                        } else {
                            text.append(character);
                        }
                        return true;
                    }
                });
            }

            @Override
            public void render() {
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

                viewport.setUnitsPerPixel(2f);
                viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
                batch.setProjectionMatrix(viewport.getCamera().combined);
                shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

                final BitmapFont font = Gdx.input.isKeyPressed(Input.Keys.SPACE) ? this.defaultFont : this.font;

                final BitmapFontCache cache = font.getCache();
                cache.clear();

                final int x = Gdx.graphics.getWidth() >> 2, y = Gdx.graphics.getHeight() - (Gdx.graphics.getHeight() >> 2);

                final GlyphLayout layout = cache.addText(text, x, y);

                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(Color.GREEN);
                shapeRenderer.rect(x,y, layout.width, -layout.height);
                shapeRenderer.setColor(Color.RED);
                shapeRenderer.line(x-10,y - font.getCapHeight(),x+layout.width+20,y - font.getCapHeight());
                shapeRenderer.end();

                batch.begin();
                cache.draw(batch);
                batch.end();
            }
        }, config);
    }
}
