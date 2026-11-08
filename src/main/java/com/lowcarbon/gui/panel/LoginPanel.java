package com.lowcarbon.gui.panel;

import com.lowcarbon.entity.User;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.gui.MainFrame;
import com.lowcarbon.service.UserService;
import com.lowcarbon.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 登录面板。
 * <p>
 * 提供用户名 + 密码登录入口，以及跳转到注册界面的入口。
 * 登录请求通过 {@link SwingWorker} 异步执行，避免阻塞事件派发线程导致界面卡顿。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class LoginPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(LoginPanel.class.getName());

    private final MainFrame mainFrame;
    private final UserService userService;

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JButton loginButton;

    /**
     * 构造登录面板。
     *
     * @param mainFrame 主窗口引用（用于登录成功后导航）
     */
    public LoginPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.userService = new UserService();

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 标题
        JLabel titleLabel = new JLabel("低碳校园资源循环流转平台", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(titleLabel, gbc);

        JLabel subtitleLabel = new JLabel("用户登录", SwingConstants.CENTER);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 15f));
        gbc.gridy = 1;
        add(subtitleLabel, gbc);

        gbc.gridwidth = 1;

        // 用户名
        gbc.gridy = 2;
        gbc.gridx = 0;
        add(new JLabel("用户名："), gbc);
        usernameField = new JTextField(18);
        gbc.gridx = 1;
        add(usernameField, gbc);

        // 密码
        gbc.gridy = 3;
        gbc.gridx = 0;
        add(new JLabel("密　码："), gbc);
        passwordField = new JPasswordField(18);
        gbc.gridx = 1;
        add(passwordField, gbc);

        // 按钮区
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        loginButton = new JButton("登　录");
        JButton toRegisterButton = new JButton("注册新账号");
        buttonPanel.add(loginButton);
        buttonPanel.add(toRegisterButton);
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        add(buttonPanel, gbc);

        // 事件绑定
        loginButton.addActionListener(e -> doLogin());
        toRegisterButton.addActionListener(e -> mainFrame.showRegister());
        // 密码框回车直接触发登录
        passwordField.addActionListener(e -> doLogin());
    }

    /**
     * 执行登录：先做输入非空校验，再异步调用 Service 层完成认证。
     */
    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        // 事件层仅做参数校验，业务规则校验由 Service 层负责
        if (Validator.isBlank(username) || Validator.isBlank(password)) {
            JOptionPane.showMessageDialog(this, "用户名和密码不能为空",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginButton.setEnabled(false);
        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() {
                return userService.login(username, password);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                try {
                    User user = get();
                    passwordField.setText("");
                    mainFrame.onLoginSuccess(user);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(LoginPanel.this, cause.getMessage(),
                                "登录失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "登录过程发生未知异常", e);
                        JOptionPane.showMessageDialog(LoginPanel.this, "系统异常，请稍后再试",
                                "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }
}
