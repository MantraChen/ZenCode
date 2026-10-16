package cn.zencode.ide.web;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.commands.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.project.facet.core.*;

/** WTP owns project/runtime configuration; only the newly created project receives sample files. */
public final class NewWebSampleCommand extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        org.eclipse.swt.widgets.Shell shell = HandlerUtil.getActiveShellChecked(event);
        Set<String> previous = new HashSet<String>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) previous.add(project.getName());
        MessageDialog.openInformation(shell, "新建 JSP / Servlet 工程", "接下来打开 WTP 工程向导。请选择本地 Tomcat 运行环境 和 JDK 8；动态 Web 模块版本选择 3.0 或以上（Tomcat 7 为 3.0，8/8.5 为 3.1，9 为 4.0）。\n完成后向新工程自动添加 JSP、Servlet 和 JDBC 示例。已有文件不会覆盖。若没有运行环境，请先使用配置本地服务器入口。");
        new WebProjectCommand().execute(event);
        IProject target = null;
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (previous.contains(project.getName()) || !project.isOpen()) continue;
            IVirtualComponent component = ComponentCore.createComponent(project);
            if (component != null && component.exists()) {
                if (target != null) { MessageDialog.openError(shell, "错误 / 工程不唯一", "检测到多个新工程，未写入模板。"); return null; }
                target = project;
            }
        }
        if (target == null) return null;
        final IProject project = target;
        try {
            IFacetedProject faceted = ProjectFacetsManager.create(project);
            if (faceted == null || faceted.getProjectFacetVersion(ProjectFacetsManager.getProjectFacet("jst.web")) == null)
                throw new CoreException(new Status(IStatus.ERROR, "cn.zencode.ide", "新工程缺少 Dynamic Web facet"));
            if (faceted.getPrimaryRuntime() == null)
                throw new CoreException(new Status(IStatus.ERROR, "cn.zencode.ide", "请先为新工程选择本地 Tomcat Targeted Runtime；未写入模板"));
            if (!"1.8".equals(JavaCore.create(project).getOption(JavaCore.COMPILER_COMPLIANCE, true)))
                throw new CoreException(new Status(IStatus.ERROR, "cn.zencode.ide", "请选择 Java 1.8 编译级别与 JDK 8；未写入模板"));
            String version = faceted.getProjectFacetVersion(ProjectFacetsManager.getProjectFacet("jst.web")).getVersionString();
            if (!version.equals("3.0") && !version.equals("3.1") && !version.equals("4.0"))
                throw new CoreException(new Status(IStatus.ERROR, "cn.zencode.ide", "请选择 Web facet 3.0 / 3.1 / 4.0，当前为 " + version));
            IContainer web = ComponentCore.createComponent(project).getRootFolder().getUnderlyingFolder();
            IContainer source = null;
            for (IClasspathEntry entry : JavaCore.create(project).getRawClasspath()) {
                if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE && project.getFullPath().isPrefixOf(entry.getPath())) {
                    source = (IContainer) ResourcesPlugin.getWorkspace().getRoot().findMember(entry.getPath());
                    if (source != null) break;
                }
            }
            if (source == null || web == null) throw new CoreException(new Status(IStatus.ERROR, "cn.zencode.ide", "无法确定 WTP 部署目录或 Java 源目录"));
            final IContainer javaRoot = source;
            final IContainer webRoot = web;
            ResourcesPlugin.getWorkspace().run(monitor -> {
                for (Map.Entry<String, String> entry : cn.zencode.assist.core.runtime.OfflineWebTemplate.files().entrySet()) {
                    String path = entry.getKey();
                    IContainer root = path.startsWith("src/") ? javaRoot : path.startsWith("WebContent/") ? webRoot : project;
                    String relative = path.startsWith("src/") ? path.substring(4) : path.startsWith("WebContent/") ? path.substring(11) : path;
                    IFile file = root.getFile(new Path(relative));
                    // WTP may already generate web.xml; annotation-based servlet needs no descriptor edits.
                    if (file.exists() && path.endsWith("web.xml")) continue;
                    if (file.exists()) throw new CoreException(new Status(IStatus.ERROR, "cn.zencode.ide", "拒绝覆盖：" + file.getFullPath()));
                }
                for (Map.Entry<String, String> entry : cn.zencode.assist.core.runtime.OfflineWebTemplate.files().entrySet()) {
                    String path = entry.getKey();
                    IContainer root = path.startsWith("src/") ? javaRoot : path.startsWith("WebContent/") ? webRoot : project;
                    String relative = path.startsWith("src/") ? path.substring(4) : path.startsWith("WebContent/") ? path.substring(11) : path;
                    IFile file = root.getFile(new Path(relative));
                    if (file.exists()) continue;
                    createParents(file.getParent(), monitor);
                    file.create(new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8)), false, monitor);
                    file.setCharset("UTF-8", monitor);
                }
            }, project, IWorkspace.AVOID_UPDATE, null);
            MessageDialog.openInformation(shell, "就绪 / 模板已创建", "已按工程实际部署与源目录加入模板：" + project.getName() + "\n选择工程后使用运行服务器 / 调试服务器入口。JDBC 驱动需自行提供；示例不自动连接数据库。");
        } catch (CoreException e) {
            MessageDialog.openError(shell, "错误 / 模板写入", e.getMessage() + "\n新建工程保留；请检查可能已写入的示例文件。");
        }
        return null;
    }
    private static void createParents(IContainer container, IProgressMonitor monitor) throws CoreException {
        if (container.exists()) return;
        createParents(container.getParent(), monitor);
        ((IFolder) container).create(false, true, monitor);
    }
}
