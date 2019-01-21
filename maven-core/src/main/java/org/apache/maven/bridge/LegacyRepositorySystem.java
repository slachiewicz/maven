package org.apache.maven.bridge;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.repository.ArtifactDoesNotExistException;
import org.apache.maven.repository.ArtifactTransferFailedException;
import org.apache.maven.repository.ArtifactTransferListener;
import org.apache.maven.repository.DelegatingLocalArtifactRepository;
import org.apache.maven.repository.LocalArtifactRepository;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyUtils;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.ArtifactResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replacement of org.apache.maven.repository.legacy.LegacyRepositorySystem from maven-compat
 * to have default implementation for RepositorySystem without maven-compat
 *
 * @since 3.6.1
 * @deprecated please use org.apache.maven.bridge.MavenRepositorySystem
 */
@Component( role = RepositorySystem.class, hint = "default" )
@Deprecated
public class LegacyRepositorySystem
        implements RepositorySystem
{
    private final static Logger LOGGER = LoggerFactory.getLogger( LegacyRepositorySystem.class );

    @Requirement
    private MavenRepositorySystem mavenRepositorySystem;

    @Requirement
    private PlexusContainer plexus;

    @Requirement
    private SettingsDecrypter settingsDecrypter;

    @Override
    public Artifact createArtifact(String groupId, String artifactId, String version, String packaging) {
        return mavenRepositorySystem.createArtifactX( groupId, artifactId, version, null, packaging, null, null );
    }

    @Override
    public Artifact createArtifact(String groupId, String artifactId, String version, String scope, String type) {
        return mavenRepositorySystem.createArtifact( groupId, artifactId, version, scope, type );
    }

    @Override
    public Artifact createProjectArtifact(String groupId, String artifactId, String version) {
        return mavenRepositorySystem.createProjectArtifact( groupId, artifactId, version );
    }

    @Override
    public Artifact createArtifactWithClassifier(String groupId, String artifactId, String version, String type, String classifier) {
        return mavenRepositorySystem.createArtifactX( groupId, artifactId, version, null, type, classifier, null );
    }

    @Override
    public Artifact createPluginArtifact(Plugin plugin) {
        return mavenRepositorySystem.createPluginArtifact( plugin );
    }

    @Override
    public Artifact createDependencyArtifact(Dependency dependency) {
        return mavenRepositorySystem.createDependencyArtifact( dependency );
    }

    @Override
    public ArtifactRepository buildArtifactRepository(Repository repository) throws InvalidRepositoryException {
        return MavenRepositorySystem.buildArtifactRepository( repository );
    }

    @Override
    public ArtifactRepository createDefaultRemoteRepository() {
        return createRepository( DEFAULT_REMOTE_REPO_URL, DEFAULT_REMOTE_REPO_ID,
                ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY, false,
                ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN );
    }
    private ArtifactRepository createRepository(String url, String repositoryId,
                                                String releaseUpdates, boolean snapshots, String snapshotUpdates,
                                                String checksumPolicy)
    {
        ArtifactRepositoryPolicy snapshotsPolicy =
                new ArtifactRepositoryPolicy( snapshots, snapshotUpdates, checksumPolicy );

        ArtifactRepositoryPolicy releasesPolicy =
                new ArtifactRepositoryPolicy(true, releaseUpdates, checksumPolicy );

        return createArtifactRepository( repositoryId, url, null, snapshotsPolicy, releasesPolicy );
    }

    @Override
    public ArtifactRepository createDefaultLocalRepository() {
        return createLocalRepository( defaultUserLocalRepository );
    }

    @Override
    public ArtifactRepository createLocalRepository(File localRepository) {
        return createRepository( "file://" + localRepository.toURI().getRawPath(), DEFAULT_LOCAL_REPO_ID,
                ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, true,
                ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE );
    }

    @Override
    public ArtifactRepository createArtifactRepository(String id, String url, ArtifactRepositoryLayout repositoryLayout,
             ArtifactRepositoryPolicy snapshots, ArtifactRepositoryPolicy releases) {
        return MavenRepositorySystem.createArtifactRepository( id, url, repositoryLayout, snapshots, releases );
    }

    /**
     * Calculates the effective repositories for the given input repositories which are assumed to be already mirrored
     * (if applicable). This process will essentially remove duplicate repositories by merging them into one equivalent
     * repository. It is worth to point out that merging does not simply choose one of the input repositories and
     * discards the others but actually combines their possibly different policies.
     *
     * @param repositories The original repositories, may be {@code null}.
     * @return The effective repositories or {@code null} if the input was {@code null}.
     */
    @Override
    public List<ArtifactRepository> getEffectiveRepositories(List<ArtifactRepository> repositories) {
        if ( repositories == null )
        {
            return null;
        }

        Map<String, List<ArtifactRepository>> reposByKey = new LinkedHashMap<>();

        for ( ArtifactRepository repository : repositories )
        {
            String key = repository.getId();

            List<ArtifactRepository> aliasedRepos = reposByKey.get( key );

            if ( aliasedRepos == null )
            {
                aliasedRepos = new ArrayList<>();
                reposByKey.put( key, aliasedRepos );
            }

            aliasedRepos.add( repository );
        }

        List<ArtifactRepository> effectiveRepositories = new ArrayList<>();

        for ( List<ArtifactRepository> aliasedRepos : reposByKey.values() )
        {
            List<ArtifactRepository> mirroredRepos = new ArrayList<>();

            List<ArtifactRepositoryPolicy> releasePolicies =
                    new ArrayList<>( aliasedRepos.size() );

            for ( ArtifactRepository aliasedRepo : aliasedRepos )
            {
                releasePolicies.add( aliasedRepo.getReleases() );
                mirroredRepos.addAll( aliasedRepo.getMirroredRepositories() );
            }

            ArtifactRepositoryPolicy releasePolicy = getEffectivePolicy( releasePolicies );

            List<ArtifactRepositoryPolicy> snapshotPolicies =
                    new ArrayList<>( aliasedRepos.size() );

            for ( ArtifactRepository aliasedRepo : aliasedRepos )
            {
                snapshotPolicies.add( aliasedRepo.getSnapshots() );
            }

            ArtifactRepositoryPolicy snapshotPolicy = getEffectivePolicy( snapshotPolicies );

            ArtifactRepository aliasedRepo = aliasedRepos.get( 0 );

            ArtifactRepository effectiveRepository =
                    createArtifactRepository( aliasedRepo.getId(), aliasedRepo.getUrl(), aliasedRepo.getLayout(),
                            snapshotPolicy, releasePolicy );

            effectiveRepository.setAuthentication( aliasedRepo.getAuthentication() );

            effectiveRepository.setProxy( aliasedRepo.getProxy() );

            effectiveRepository.setMirroredRepositories( mirroredRepos );

            effectiveRepositories.add( effectiveRepository );
        }

        return effectiveRepositories;
    }

    private ArtifactRepositoryPolicy getEffectivePolicy( Collection<ArtifactRepositoryPolicy> policies )
    {
        ArtifactRepositoryPolicy effectivePolicy = null;

        for ( ArtifactRepositoryPolicy policy : policies )
        {
            if ( effectivePolicy == null )
            {
                effectivePolicy = new ArtifactRepositoryPolicy( policy );
            }
            else
            {
                effectivePolicy.merge( policy );
            }
        }

        return effectivePolicy;
    }

    @Override
    public Mirror getMirror(ArtifactRepository repository, List<Mirror> mirrors) {
        return MavenRepositorySystem.getMirror( repository, mirrors );
    }

    @Override
    public void injectMirror(List<ArtifactRepository> repositories, List<Mirror> mirrors) {
        mavenRepositorySystem.injectMirror(repositories, mirrors );
    }

    /**
     * Injects the proxy information into the specified repositories. For each repository that is matched by a proxy,
     * its proxy data will be set accordingly. Repositories without a matching proxy will have their proxy cleared.
     * <em>Note:</em> This method must be called after {@link #injectMirror(List, List)} or the repositories will end up
     * with the wrong proxies.
     *
     * @param repositories The repositories into which to inject the proxy information, may be {@code null}.
     * @param proxies      The available proxies, may be {@code null}.
     */
    @Override
    public void injectProxy( List<ArtifactRepository> repositories, List<org.apache.maven.settings.Proxy> proxies )
    {
        if ( repositories != null )
        {
            for ( ArtifactRepository repository : repositories )
            {
                org.apache.maven.settings.Proxy proxy = getProxy( repository, proxies );

                if ( proxy != null )
                {
                    SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest( proxy );
                    SettingsDecryptionResult result = settingsDecrypter.decrypt( request );
                    proxy = result.getProxy();

                    if ( LOGGER.isDebugEnabled() )
                    {
                        for ( SettingsProblem problem : result.getProblems() )
                        {
                            LOGGER.debug( problem.getMessage(), problem.getException() );
                        }
                    }

                    org.apache.maven.repository.Proxy p = new org.apache.maven.repository.Proxy();
                    p.setHost( proxy.getHost() );
                    p.setProtocol( proxy.getProtocol() );
                    p.setPort( proxy.getPort() );
                    p.setNonProxyHosts( proxy.getNonProxyHosts() );
                    p.setUserName( proxy.getUsername() );
                    p.setPassword( proxy.getPassword() );

                    repository.setProxy( p );
                }
                else
                {
                    repository.setProxy( null );
                }
            }
        }
    }

    private org.apache.maven.settings.Proxy getProxy( ArtifactRepository repository,
                                                      List<org.apache.maven.settings.Proxy> proxies )
    {
        if ( proxies != null && repository.getProtocol() != null )
        {
            for ( org.apache.maven.settings.Proxy proxy : proxies )
            {
                if ( proxy.isActive() && repository.getProtocol().equalsIgnoreCase( proxy.getProtocol() ) )
                {
                    if ( StringUtils.isNotEmpty( proxy.getNonProxyHosts() ) )
                    {
                        ProxyInfo pi = new ProxyInfo();
                        pi.setNonProxyHosts( proxy.getNonProxyHosts() );

                        org.apache.maven.wagon.repository.Repository repo =
                                new org.apache.maven.wagon.repository.Repository( repository.getId(), repository.getUrl() );

                        if ( !ProxyUtils.validateNonProxyHosts( pi, repo.getHost() ) )
                        {
                            return proxy;
                        }
                    }
                    else
                    {
                        return proxy;
                    }
                }
            }
        }

        return null;
    }


    /**
     * Injects the authentication information into the specified repositories. For each repository that is matched by a
     * server, its credentials will be updated to match the values from the server specification. Repositories without a
     * matching server will have their credentials cleared. <em>Note:</em> This method must be called after
     * {@link #injectMirror(List, List)} or the repositories will end up with the wrong credentials.
     *
     * @param repositories The repositories into which to inject the authentication information, may be {@code null}.
     * @param servers      The available servers, may be {@code null}.
     */
    @Override
    public void injectAuthentication( List<ArtifactRepository> repositories, List<Server> servers )
    {
        if ( repositories != null )
        {
            Map<String, Server> serversById = new HashMap<>();

            if ( servers != null )
            {
                for ( Server server : servers )
                {
                    if ( !serversById.containsKey( server.getId() ) )
                    {
                        serversById.put( server.getId(), server );
                    }
                }
            }

            for ( ArtifactRepository repository : repositories )
            {
                Server server = serversById.get( repository.getId() );

                if ( server != null )
                {
                    SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest( server );
                    SettingsDecryptionResult result = settingsDecrypter.decrypt( request );
                    server = result.getServer();

                    if ( LOGGER.isDebugEnabled() )
                    {
                        for ( SettingsProblem problem : result.getProblems() )
                        {
                            LOGGER.debug( problem.getMessage(), problem.getException() );
                        }
                    }

                    Authentication authentication = new Authentication( server.getUsername(), server.getPassword() );
                    authentication.setPrivateKey( server.getPrivateKey() );
                    authentication.setPassphrase( server.getPassphrase() );

                    repository.setAuthentication( authentication );
                }
                else
                {
                    repository.setAuthentication( null );
                }
            }
        }
    }

    @Override
    public void injectMirror(RepositorySystemSession session, List<ArtifactRepository> repositories) {
        mavenRepositorySystem.injectMirror( session, repositories );
    }

    @Override
    public void injectProxy(RepositorySystemSession session, List<ArtifactRepository> repositories) {
        mavenRepositorySystem.injectProxy( session, repositories );
    }

    @Override
    public void injectAuthentication(RepositorySystemSession session, List<ArtifactRepository> repositories) {
        mavenRepositorySystem.injectAuthentication( session, repositories );
    }

    @Override
    public ArtifactResolutionResult resolve( ArtifactResolutionRequest request )
    {
        throw new IllegalArgumentException( "Legacy Maven 2.x resolve() not supported" );
    }


    @Override
    public void publish(ArtifactRepository repository, File source, String remotePath, ArtifactTransferListener transferListener) throws ArtifactTransferFailedException {
        throw new IllegalArgumentException( "Legacy Maven 2.x publish() not supported" );
    }

    @Override
    public void retrieve(ArtifactRepository repository, File destination, String remotePath, ArtifactTransferListener transferListener) throws ArtifactTransferFailedException, ArtifactDoesNotExistException {
        throw new IllegalArgumentException( "Legacy Maven 2.x resolve() not supported" );
    }
}
