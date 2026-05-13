package com.g2rain.iam.controller;


import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;


/**
 * 页面控制器，负责渲染登录和授权同意页面。
 * <p>
 * 该控制器用于处理客户端的登录和授权请求，渲染登录页面和授权同意页面，并在需要时处理用户会话。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 通过 GET 请求跳转到登录页
 * /auth/login?clientId=client123&redirectUri=http://example.com/callback
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/11
 */
@Controller
@AllArgsConstructor
public class PageController {

    /**
     * 资源加载器，用于检查模板文件是否存在。
     */
    private ResourceLoader resourceLoader;

    /**
     * IAM / 平台对外地址，用于首页「立即登录」绝对跳转。
     */
    private IamAccessProperties iamAccessProperties;

    /**
     * 登录页钉钉入口使用的 {@code bindMode} 等配置。
     */
    private DingTalkIamProperties dingTalkIamProperties;

    /**
     * 注册页面渲染方法，处理 /auth/register.html 路径。
     * <p>
     * 该方法专门用于渲染注册页面，接收 OAuth 授权流程中的参数（clientId、redirectUri、state），
     * 并将这些参数传递给模板，以便在注册完成后能够正确跳转回登录页面。
     * </p>
     * <p>
     * 使用示例：
     * <pre>{@code
     * // 通过 GET 请求跳转到注册页
     * /auth/register.html?clientId=client123&redirectUri=http://example.com/callback&state=xyz
     * }</pre>
     * </p>
     *
     * @param clientId    客户端 ID（可选）
     * @param redirectUri 登录后重定向的 URI（可选）
     * @param state       请求的状态参数，通常用于防止 CSRF 攻击（可选）
     * @param model       用于向视图传递数据的模型
     * @return {@link ModelAndView}，包含注册页面视图
     */
    @GetMapping(value = "/auth/register.html")
    public ModelAndView registerPage(@RequestParam(name = "clientId", required = false) String clientId,
                                    @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                    @RequestParam(name = "state", required = false) String state,
                                    Model model) {
        // 将 URL 参数传递到视图，供注册表单使用
        model.addAttribute("clientId", clientId);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("state", state);
        return new ModelAndView("register");
    }

    /**
     * 通用页面渲染方法，处理 /auth/*.html 路径。
     * <p>
     * 该方法会根据路径中的文件名查找对应的模板文件。如果模板存在，则渲染该模板；
     * 如果模板不存在，则返回错误页面并提示路径不存在。
     * </p>
     * <p>
     * 使用示例：
     * <pre>{@code
     * // 访问 /auth/index.html 会渲染 templates/index.html
     * // 访问 /auth/test.html 如果模板不存在，会显示错误页面
     * }</pre>
     * </p>
     *
     * @param filename 模板文件名（不包含 .html 后缀）
     * @param model    用于向视图传递数据的模型
     * @return {@link ModelAndView}，包含模板视图或错误页面视图
     */
    @GetMapping(value = "/auth/{filename}.html")
    public ModelAndView dynamicPage(@PathVariable(name = "filename") String filename,
                                    @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                    @RequestParam(name = "clientId", required = false) String clientId,
                                    @RequestParam(name = "state", required = false) String state,
                                    Model model) {
        // 防止路径遍历攻击，确保文件名只包含合法字符
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            model.addAttribute("error", "非法的路径参数");
            model.addAttribute("redirectUri", "");
            return new ModelAndView("error");
        }

        // 检查模板文件是否存在
        String templatePath = "classpath:/templates/" + filename + ".html";
        Resource resource = resourceLoader.getResource(templatePath);

        if (resource.exists() && resource.isReadable()) {
            // 模板存在，返回对应的视图
            if ("index".equals(filename)) {
                applyIndexLoginRedirect(model, redirectUri);
            }
            if ("login".equals(filename)) {
                model.addAttribute("clientId", clientId != null ? clientId : "");
                model.addAttribute("redirectUri", redirectUri != null ? redirectUri : "");
                model.addAttribute("state", state != null ? state : "");
                String m = dingTalkIamProperties.getLoginPageBindMode();
                if (Strings.isNotBlank(m)) {
                    model.addAttribute("dingTalkBindMode", m.trim());
                }
            }
            return new ModelAndView(filename);
        } else {
            // 模板不存在，返回错误页面
            String requestPath = "/auth/" + filename + ".html";
            model.addAttribute("error", "请求的页面不存在: " + requestPath);
            model.addAttribute("redirectUri", "");
            return new ModelAndView("error");
        }
    }

    /**
     * 首页「立即登录」跳转目标：
     * <ul>
     *     <li>若请求参数 {@code redirectUri} 非空且通过校验：跳转到该地址（由对方页面再重定向回 {@code /auth/authorize?...}）</li>
     *     <li>若 {@code redirectUri} 为空或非法：由模板将 {@code platformBaseUrl} 与 {@code /main/home} 拼成绝对链接跳转</li>
     * </ul>
     */
    private void applyIndexLoginRedirect(Model model, String redirectUri) {
        model.addAttribute("platformBaseUrl", resolvePlatformBaseUrl());
        String resolved = resolveIndexLoginRedirectUri(redirectUri);
        if (resolved == null) {
            model.addAttribute("loginViaRedirectUri", false);
        } else {
            model.addAttribute("loginViaRedirectUri", true);
            model.addAttribute("loginRedirectUri", resolved);
        }
    }

    /**
     * 控制台对外根 URL（无尾斜杠）：{@code platform-base-url}，未配置时回退为 IAM {@code access-base-url}。
     */
    private String resolvePlatformBaseUrl() {
        return iamAccessProperties.resolvedPlatformBaseUrl();
    }

    /**
     * @return 合法跳转地址；若应使用默认控制台则返回 null
     */
    private String resolveIndexLoginRedirectUri(String redirectUri) {
        if (Strings.isBlank(redirectUri)) {
            return null;
        }
        String trimmed = redirectUri.trim();
        // 站内相对路径：必须以单个 / 开头，禁止 // 开头的协议相对 URL
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return trimmed;
        }
        // 绝对地址：仅允许 http(s)，禁止 javascript:、data: 等
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return trimmed;
        }
        return null;
    }
}
