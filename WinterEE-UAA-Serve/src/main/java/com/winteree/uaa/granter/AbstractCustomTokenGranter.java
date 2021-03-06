package com.winteree.uaa.granter;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.Map;

/**
 * 自定义令牌颁发
 *
 * @author RenFei
 */
public abstract class AbstractCustomTokenGranter extends AbstractTokenGranter {
    private final OAuth2RequestFactory requestFactory;

    protected AbstractCustomTokenGranter(AuthorizationServerTokenServices tokenServices, ClientDetailsService clientDetailsService, OAuth2RequestFactory requestFactory, String grantType) {
        super(tokenServices, clientDetailsService, requestFactory, grantType);
        this.requestFactory = requestFactory;
    }

    @Override
    protected OAuth2Authentication getOAuth2Authentication(ClientDetails client, TokenRequest tokenRequest) {
        Map<String, String> parameters = tokenRequest.getRequestParameters();
        User customUser = getCustomUser(parameters);
        if (customUser == null) {
            throw new InvalidGrantException("无法获取用户信息");
        }
        OAuth2Request storedOAuth2Request = this.requestFactory.createOAuth2Request(client, tokenRequest);
        PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(customUser, null, customUser.getAuthorities());
        authentication.setDetails(customUser);
        OAuth2Authentication oAuth2Authentication = new OAuth2Authentication(storedOAuth2Request, authentication);
        return oAuth2Authentication;
    }

    /**
     * 根据自定义条件获取User
     *
     * @param parameters 参数列表
     * @return User
     */
    protected abstract User getCustomUser(Map<String, String> parameters);
}
