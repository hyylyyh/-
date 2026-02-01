import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.jungleadventure.shared.ClasspathResourceReader
import com.jungleadventure.shared.GameApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "丛林大冒险") {
        GameApp(resourceReader = ClasspathResourceReader())
    }
}