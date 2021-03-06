/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.SpaceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Space;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.SpaceSummary;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPageResources;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class Spaces {
  private final SpaceService api;
  private final Organizations organizations;

  private final LoadingCache<String, CloudFoundrySpace> spaceCache = CacheBuilder
    .newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(new CacheLoader<String, CloudFoundrySpace>() {
      @Override
      public CloudFoundrySpace load(@Nonnull String guid) throws CloudFoundryApiException, ResourceNotFoundException {
        return safelyCall(() -> api.findById(guid))
          .map(Spaces.this::map)
          .orElseThrow(ResourceNotFoundException::new);
      }
    });

  public CloudFoundrySpace findById(String guid) throws CloudFoundryApiException {
    try {
      return spaceCache.get(guid);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ResourceNotFoundException)
        return null;
      throw new CloudFoundryApiException(e.getCause(), "Unable to find space by id");
    }
  }

  public List<CloudFoundrySpace> all() throws CloudFoundryApiException {
    return collectPageResources("spaces", pg -> api.all(pg, null))
      .stream().map(this::map).collect(toList());
  }

  @Nullable
  public SpaceSummary getSpaceSummaryById(String spaceId) {
    return safelyCall(() -> api.getSpaceSummaryById(spaceId))
      .orElse(null);
  }

  @Nullable
  public CloudFoundrySpace findByName(String orgId, String spaceName) throws CloudFoundryApiException {
    return safelyCall(() -> api.all(null, Arrays.asList("name:" + spaceName, "organization_guid:" + orgId)))
      .flatMap(page -> page.getResources().stream().findAny().map(this::map))
      .orElse(null);
  }

  private CloudFoundrySpace map(Resource<Space> res) throws CloudFoundryApiException {
    return CloudFoundrySpace.builder()
      .id(res.getMetadata().getGuid())
      .name(res.getEntity().getName())
      .organization(organizations.findById(res.getEntity().getOrganizationGuid()))
      .build();
  }
}
