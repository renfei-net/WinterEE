package com.winteree.uaa.service.impl;

import com.winteree.api.entity.LogSubTypeEnum;
import com.winteree.api.entity.RunModeEnum;
import com.winteree.api.entity.ValidationType;
import com.winteree.api.entity.VerificationCodeDTO;
import com.winteree.uaa.client.WintereeCoreServiceClient;
import com.winteree.uaa.config.WintereeUaaConfig;
import com.winteree.uaa.dao.entity.AccountDO;
import com.winteree.uaa.service.AccountService;
import com.winteree.uaa.service.I18nService;
import com.winteree.uaa.service.LogService;
import com.winteree.uaa.service.SecretKeyService;
import net.renfei.sdk.comm.StateCode;
import net.renfei.sdk.entity.APIResult;
import net.renfei.sdk.utils.AESUtil;
import net.renfei.sdk.utils.PasswordUtils;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * @author RenFei
 */
@Service
public class CustomUserDetailsServiceImpl implements UserDetailsService {
    private final WintereeCoreServiceClient wintereeCoreServiceClient;
    private final AccountService accountService;
    private final I18nService i18NService;
    private final SecretKeyService secretKeyService;
    private final LogService logService;
    private final WintereeUaaConfig wintereeUaaConfig;
    /**
     * 密码错误次数锁定
     */
    public static final int PASSWORD_ERROR_TIMES_LOCKED = 3;

    public CustomUserDetailsServiceImpl(WintereeCoreServiceClient wintereeCoreServiceClient,
                                        AccountService accountService,
                                        I18nService i18NService,
                                        SecretKeyService secretKeyService,
                                        LogService logService,
                                        WintereeUaaConfig wintereeUaaConfig) {
        this.wintereeCoreServiceClient = wintereeCoreServiceClient;
        this.accountService = accountService;
        this.i18NService = i18NService;
        this.secretKeyService = secretKeyService;
        this.logService = logService;
        this.wintereeUaaConfig = wintereeUaaConfig;
    }

    /**
     * 使用用户名和密码登陆
     *
     * @param userName 用户名
     * @param password 密码
     * @param language 语言
     * @param keyid    AES秘钥ID
     * @return Spring Security 的对象
     */
    public User loadUserByUsernameAndPassword(String userName, String password, String language, String keyid) {
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(password)) {
            throw new InvalidGrantException(i18NService.getMessage(language, "uaa.invalidusernameorpassword", "无效的用户名或密码"));
        }
        // 判断成功后返回用户细节
        AccountDO accountDO = getAccountByName(userName);
        if (accountDO == null) {
            throw new UsernameNotFoundException(i18NService.getMessage(language, "uaa.invalidusernameorpassword", "无效的用户名或密码"));
        }
        // 检查账户的状态
        checkAccountState(accountDO, language);
        // AES解密密码
        password = decryptPassword(password, language, keyid);
        if (!PasswordUtils.verifyPassword(password, accountDO.getPasswd())) {
            // 错误计数
            accountPasswordErrorCont(accountDO);
            logService.log(accountDO.getUuid(), LogSubTypeEnum.FAIL, "密码错误，拒绝登陆");
            throw new UsernameNotFoundException(i18NService.getMessage(language, "uaa.invalidusernameorpassword", "无效的用户名或密码"));
        }
        //TODO TOTP两步验证
        return getUser(accountDO);
    }

    /**
     * 使用验证码登陆
     *
     * @param userName 用户名（手机号）
     * @param code     验证码
     * @param language 语言
     * @param keyid    AES秘钥ID
     * @return Spring Security 的对象
     */
    public User loadUserByVerificationCode(String userName, String code, String language, String keyid) {
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(code)) {
            throw new InvalidGrantException(i18NService.getMessage(language, "uaa.invalidphonenumberorverificationcode", "无效的手机号或验证码"));
        }
        // 判断成功后返回用户细节
        AccountDO accountDO = getAccountByName(userName);
        if (accountDO == null) {
            throw new UsernameNotFoundException(i18NService.getMessage(language, "uaa.invalidphonenumberorverificationcode", "无效的手机号或验证码"));
        }
        // 检查账户的状态
        checkAccountState(accountDO, language);
        // AES解密密码
        code = decryptPassword(code, language, keyid);
        // 验证验证码
        APIResult<VerificationCodeDTO> apiResult =
                wintereeCoreServiceClient.getVerificationCode(accountDO.getUserName(), ValidationType.SIGNUP.value());
        if (!StateCode.OK.getCode().equals(apiResult.getCode()) || apiResult.getData() == null) {
            throw new UsernameNotFoundException("验证码错误，请重新获取新的验证码");
        }
        if (!apiResult.getData().getVerificationCode().equals(code)) {
            throw new UsernameNotFoundException("验证码错误，请重新获取新的验证码");
        }
        // 密码错误会锁定账户，为啥验证码错误不会锁定账户呢？因为密码是静态的不会变，会被暴力破解到，验证码是动态的
        //TODO TOTP两步验证
        return getUser(accountDO);
    }

    /**
     * 账户密码错误计数
     *
     * @param accountDO 账户对象
     */
    private void accountPasswordErrorCont(AccountDO accountDO) {
        if (RunModeEnum.DEMO.getMode().equals(wintereeUaaConfig.getRunMode())) {
            // 演示模式不记录错误数
        } else {
            if (accountDO != null) {
                if (accountDO.getErrorCount() == null) {
                    accountDO.setErrorCount(1);
                } else {
                    accountDO.setErrorCount(accountDO.getErrorCount() + 1);
                    if (accountDO.getErrorCount() > PASSWORD_ERROR_TIMES_LOCKED) {
                        // 锁定时间，每错误一次增加10秒锁定时间
                        int lockMin = (accountDO.getErrorCount() - 1) * 10;
                        accountDO.setLockTime(new Date(System.currentTimeMillis() + lockMin * 1000));
                    }
                }
                accountService.updateByPrimaryKeySelective(accountDO);
            }
        }
    }

    /**
     * 检查账户的状态
     *
     * @param accountDO
     * @param language
     */
    private void checkAccountState(AccountDO accountDO, String language) {
        if (accountDO.getLockTime() != null &&
                new Date().before(accountDO.getLockTime())) {
            long min = (accountDO.getLockTime().getTime() - System.currentTimeMillis()) / 1000;
            logService.log(accountDO.getUuid(), LogSubTypeEnum.FAIL, "账户被锁定，请稍后再试");
            throw new LockedException(i18NService.getMessage(language, "uaa.accountlocked", "账户被锁定，请稍后再试"));
        }
        if (accountDO.getUserStatus() <= 0) {
            logService.log(accountDO.getUuid(), LogSubTypeEnum.FAIL, "账户未激活或被禁用");
            throw new DisabledException(i18NService.getMessage(language, "uaa.Accountnotactivatedordisabled", "账户未激活或被禁用"));
        }
    }

    /**
     * 获取 UserDetails
     *
     * @param accountDO 来自数据库的账户对象
     * @return Spring Security 的对象
     */
    private User getUser(AccountDO accountDO) {
        logService.log(accountDO.getUuid(), LogSubTypeEnum.SUCCESS, "登陆成功");
        return new User(accountDO.getUuid(), accountDO.getPasswd(), accountService.getGrantedAuthority(accountDO));
    }

    private AccountDO getAccountByName(String name) {
        AccountDO accountDO = null;
        if (net.renfei.sdk.utils.StringUtils.isEmail(name)) {
            accountDO = accountService.findAccountByEmail(name);
        } else if (net.renfei.sdk.utils.StringUtils.isEmail(name)) {
            accountDO = accountService.findAccountByPhoneNumber(name);
        } else {
            accountDO = accountService.findAccountByUsername(name);
        }
        return accountDO;
    }

    /**
     * 解密密码
     *
     * @param password 密文密码
     * @param language 语言
     * @param keyId    AES的ID
     * @return 明文密码
     */
    private String decryptPassword(String password, String language, String keyId) {
        String aesKey = secretKeyService.getSecretKeyStringById(keyId);
        if (aesKey == null) {
            throw new InvalidGrantException(i18NService.getMessage(language, "uaa.aeskeyiddoesnotexist", "AESKeyId不存在"));
        }
        try {
            password = AESUtil.decrypt(password, aesKey);
        } catch (Exception ex) {
            throw new InvalidGrantException(i18NService.getMessage(language, "uaa.passworddecryptionfailed", "密码解密失败"));
        }
        return password;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return null;
    }
}
