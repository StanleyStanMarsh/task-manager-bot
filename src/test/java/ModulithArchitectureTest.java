import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import ru.spbstu.hsai.Main;

class ModulithArchitectureTest {
    @Test
    void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(Main.class);
        modules.verify();
    }
}