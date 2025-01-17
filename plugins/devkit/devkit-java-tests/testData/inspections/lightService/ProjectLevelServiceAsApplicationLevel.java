import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
final class MyService {
  void foo() {
    MyService service = <warning descr="The project-level service is retrieved as an application-level service">ApplicationManager.getApplication().getService(MyService.class)</warning>;
  }
}
