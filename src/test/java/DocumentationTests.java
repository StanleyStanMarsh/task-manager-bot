import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import ru.spbstu.hsai.Main;

class DocumentationTests {

    ApplicationModules modules = ApplicationModules.of(Main.class);

    @Test
    void writeDocumentationSnippets() {

        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases(Documenter.CanvasOptions.defaults())
                .writeDocumentation()
                .writeAggregatingDocument();
    }
}