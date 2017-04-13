/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.project.ws;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.ProjectPermissions;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.permission.index.PermissionIndexer;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class ChangeVisibilityActionTest {
  private static final String PARAM_VISIBILITY = "visibility";
  private static final String PARAM_PROJECT = "project";
  private static final String PUBLIC = "public";
  private static final String PRIVATE = "private";
  private static final Set<String> ORGANIZATION_PERMISSIONS_NAME_SET = stream(OrganizationPermission.values()).map(OrganizationPermission::getKey)
    .collect(MoreCollectors.toSet(OrganizationPermission.values().length));
  private static final Set<String> PROJECT_PERMISSIONS_BUT_USER_AND_CODEVIEWER = ProjectPermissions.ALL.stream()
    .filter(perm -> !perm.equals(UserRole.USER) && !perm.equals(UserRole.CODEVIEWER)).collect(MoreCollectors.toSet(ProjectPermissions.ALL.size() - 2));

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone()
    .logIn();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private DbClient dbClient = dbTester.getDbClient();
  private DbSession dbSession = dbTester.getSession();
  private PermissionIndexer permissionIndexer = mock(PermissionIndexer.class);

  private ChangeVisibilityAction underTest = new ChangeVisibilityAction(dbClient, new ComponentFinder(dbClient), userSessionRule, permissionIndexer);
  private WsActionTester actionTester = new WsActionTester(underTest);

  private final Random random = new Random();
  private final String randomVisibility = random.nextBoolean() ? PUBLIC : PRIVATE;
  private final TestRequest request = actionTester.newRequest();
  private final OrganizationDto organization = dbTester.organizations().insert();

  @Test
  public void execute_fails_if_user_is_not_logged_in() {
    userSessionRule.anonymous();

    expectedException.expect(UnauthorizedException.class);
    expectedException.expectMessage("Authentication is required");

    request.execute();
  }

  @Test
  public void execute_fails_with_IAE_when_project_parameter_is_not_provided() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The 'project' parameter is missing");

    request.execute();
  }

  @Test
  public void execute_fails_with_IAE_when_project_parameter_is_not_empty() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The 'project' parameter is missing");

    request.execute();
  }

  @Test
  public void execute_fails_with_IAE_when_parameter_visibility_is_not_provided() {
    request.setParam(PARAM_PROJECT, "foo");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("The 'visibility' parameter is missing");

    request.execute();
  }

  @Test
  public void execute_fails_with_IAE_when_parameter_visibility_is_empty() {
    request.setParam(PARAM_PROJECT, "foo")
      .setParam(PARAM_VISIBILITY, "");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Value of parameter '" + PARAM_VISIBILITY + "' () must be one of: [public, private]");

    request.execute();
  }

  @Test
  public void execute_fails_with_IAE_when_value_of_parameter_visibility_is_not_lowercase() {
    request.setParam(PARAM_PROJECT, "foo");

    Stream.of("PUBLIC", "pUBliC", "PRIVATE", "PrIVAtE")
      .forEach(visibility -> {
        try {
          request.setParam(PARAM_VISIBILITY, visibility).execute();
          fail("An exception should have been raised");
        } catch (IllegalArgumentException e) {
          assertThat(e.getMessage()).isEqualTo(format("Value of parameter '%s' (%s) must be one of: %s", PARAM_VISIBILITY, visibility, "[public, private]"));
        }
      });
  }

  @Test
  public void execute_fails_with_NotFoundException_when_specified_component_does_not_exist() {
    request.setParam(PARAM_PROJECT, "foo")
      .setParam(PARAM_VISIBILITY, randomVisibility);

    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("Component key 'foo' not found");

    request.execute();
  }

  @Test
  public void execute_fails_with_BadRequestException_if_specified_component_is_neither_a_project_nor_a_view() {
    ComponentDto project = dbTester.components().insertProject(organization);
    ComponentDto module = ComponentTesting.newModuleDto(project);
    ComponentDto dir = ComponentTesting.newDirectory(project, "path");
    ComponentDto file = ComponentTesting.newFileDto(project);
    dbTester.components().insertComponents(module, dir, file);
    ComponentDto view = dbTester.components().insertView(organization);
    ComponentDto subView = ComponentTesting.newSubView(view);
    ComponentDto projectCopy = ComponentTesting.newProjectCopy("foo", project, subView);
    dbTester.components().insertComponents(subView, projectCopy);

    Stream.of(module, dir, file, subView, projectCopy)
      .forEach(nonRootComponent -> {
        request.setParam(PARAM_PROJECT, nonRootComponent.key())
          .setParam(PARAM_VISIBILITY, randomVisibility);

        try {
          request.execute();
          fail("a BadRequestException should have been raised");
        } catch (BadRequestException e) {
          assertThat(e.getMessage()).isEqualTo("Component must either be a project or a view");
        }
      });
  }

  @Test
  public void execute_throws_ForbiddenException_if_user_has_no_permission_on_specified_component() {
    ComponentDto project = dbTester.components().insertProject(organization);
    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, randomVisibility);

    expectInsufficientPrivilegeException();

    request.execute();
  }

  @Test
  public void execute_throws_ForbiddenException_if_user_has_all_permissions_but_ADMIN_on_specified_component() {
    ComponentDto project = dbTester.components().insertProject(organization);
    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, randomVisibility);
    Stream.of(UserRole.CODEVIEWER, UserRole.ISSUE_ADMIN, UserRole.USER)
      .forEach(role -> userSessionRule.addProjectUuidPermissions(role, project.uuid()));
    Arrays.stream(OrganizationPermission.values())
      .forEach(perm -> userSessionRule.addPermission(perm, organization));
    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, randomVisibility);

    expectInsufficientPrivilegeException();

    request.execute();
  }

  @Test
  public void execute_throws_BadRequestException_if_specified_component_has_pending_tasks() {
    ComponentDto project = dbTester.components().insertProject(organization);
    IntStream.range(0, 1 + Math.abs(random.nextInt(5)))
      .forEach(i -> insertPendingTask(project));
    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, randomVisibility);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("Component visibility can't be changed as long as it has background task(s) pending or in progress");

    request.execute();
  }

  @Test
  public void execute_throws_BadRequestException_if_specified_component_has_in_progress_tasks() {
    ComponentDto project = dbTester.components().insertProject(organization);
    IntStream.range(0, 1 + Math.abs(random.nextInt(5)))
      .forEach(i -> insertInProgressTask(project));
    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, randomVisibility);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("Component visibility can't be changed as long as it has background task(s) pending or in progress");

    request.execute();
  }

  @Test
  public void execute_changes_private_flag_of_specified_project_and_all_children_to_specified_new_visibility() {
    boolean initiallyPrivate = random.nextBoolean();
    ComponentDto project = dbTester.components().insertProject(organization, dto -> dto.setPrivate(initiallyPrivate));
    ComponentDto module = ComponentTesting.newModuleDto(project);
    ComponentDto dir = ComponentTesting.newDirectory(project, "path");
    ComponentDto file = ComponentTesting.newFileDto(project);
    dbTester.components().insertComponents(module, dir, file);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, initiallyPrivate ? PUBLIC : PRIVATE)
      .execute();

    assertThat(isPrivateInDb(project)).isEqualTo(!initiallyPrivate);
    assertThat(isPrivateInDb(module)).isEqualTo(!initiallyPrivate);
    assertThat(isPrivateInDb(dir)).isEqualTo(!initiallyPrivate);
    assertThat(isPrivateInDb(file)).isEqualTo(!initiallyPrivate);
  }

  @Test
  public void execute_changes_private_flag_of_specified_view_and_all_children_to_specified_new_visibility() {
    boolean initiallyPrivate = random.nextBoolean();
    ComponentDto project = dbTester.components().insertProject(organization);
    ComponentDto view = dbTester.components().insertView(organization, dto -> dto.setPrivate(initiallyPrivate));
    ComponentDto subView = ComponentTesting.newSubView(view);
    ComponentDto projectCopy = ComponentTesting.newProjectCopy("foo", project, subView);
    dbTester.components().insertComponents(subView, projectCopy);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, view.uuid());

    request.setParam(PARAM_PROJECT, view.key())
      .setParam(PARAM_VISIBILITY, initiallyPrivate ? PUBLIC : PRIVATE)
      .execute();

    assertThat(isPrivateInDb(view)).isEqualTo(!initiallyPrivate);
    assertThat(isPrivateInDb(subView)).isEqualTo(!initiallyPrivate);
    assertThat(isPrivateInDb(projectCopy)).isEqualTo(!initiallyPrivate);
  }

  @Test
  public void execute_has_no_effect_if_specified_project_already_has_specified_visibility() {
    boolean initiallyPrivate = random.nextBoolean();
    ComponentDto project = dbTester.components().insertProject(organization, dto -> dto.setPrivate(initiallyPrivate));
    ComponentDto module = ComponentTesting.newModuleDto(project)
      .setPrivate(initiallyPrivate);
    ComponentDto dir = ComponentTesting.newDirectory(project, "path")
      // child is inconsistent with root (should not occur) and won't be fixed
      .setPrivate(!initiallyPrivate);
    ComponentDto file = ComponentTesting.newFileDto(project)
      .setPrivate(initiallyPrivate);
    dbTester.components().insertComponents(module, dir, file);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, initiallyPrivate ? PRIVATE : PUBLIC)
      .execute();

    assertThat(isPrivateInDb(project)).isEqualTo(initiallyPrivate);
    assertThat(isPrivateInDb(module)).isEqualTo(initiallyPrivate);
    assertThat(isPrivateInDb(dir)).isEqualTo(!initiallyPrivate);
    assertThat(isPrivateInDb(file)).isEqualTo(initiallyPrivate);
  }

  @Test
  public void execute_has_no_effect_if_specified_view_already_has_specified_visibility() {
    boolean initiallyPrivate = random.nextBoolean();
    ComponentDto project = dbTester.components().insertProject(organization);
    ComponentDto view = dbTester.components().insertView(organization, dto -> dto.setPrivate(initiallyPrivate));
    ComponentDto subView = ComponentTesting.newSubView(view)
      // child is inconsistent with root (should not occur) and won't be fixed
      .setPrivate(!initiallyPrivate);
    ComponentDto projectCopy = ComponentTesting.newProjectCopy("foo", project, subView)
      .setPrivate(initiallyPrivate);
    dbTester.components().insertComponents(subView, projectCopy);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, view.uuid());

    request.setParam(PARAM_PROJECT, view.key())
      .setParam(PARAM_VISIBILITY, initiallyPrivate ? PRIVATE : PUBLIC)
      .execute();

    assertThat(isPrivateInDb(view)).isEqualTo(initiallyPrivate);
    assertThat(isPrivateInDb(subView)).isEqualTo(!initiallyPrivate);
    assertThat(isPrivateInDb(projectCopy)).isEqualTo(initiallyPrivate);
  }

  @Test
  public void execute_deletes_all_permissions_to_Anyone_on_specified_project_when_new_visibility_is_private() {
    ComponentDto project = dbTester.components().insertProject(organization, dto -> dto.setPrivate(false));
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    giveAllPermissionsToRootComponent(project, user, group);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, PRIVATE)
      .execute();

    verifyHasAllPermissionsButProjectPermissionsToGroupAnyOne(project, user, group);
  }

  @Test
  public void execute_deletes_all_permissions_to_Anyone_on_specified_view_when_new_visibility_is_private() {
    ComponentDto view = dbTester.components().insertView(organization, dto -> dto.setPrivate(false));
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    giveAllPermissionsToRootComponent(view, user, group);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, view.uuid());

    request.setParam(PARAM_PROJECT, view.key())
      .setParam(PARAM_VISIBILITY, PRIVATE)
      .execute();

    verifyHasAllPermissionsButProjectPermissionsToGroupAnyOne(view, user, group);
  }

  @Test
  public void execute_does_not_delete_all_permissions_to_AnyOne_on_specified_project_if_already_private() {
    ComponentDto project = dbTester.components().insertProject(organization, dto -> dto.setPrivate(true));
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    giveAllPermissionsToRootComponent(project, user, group);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, PRIVATE)
      .execute();

    verifyStillHasAllPermissions(project, user, group);
  }

  @Test
  public void execute_does_not_delete_all_permissions_to_AnyOne_on_specified_view_if_already_private() {
    ComponentDto view = dbTester.components().insertView(organization, dto -> dto.setPrivate(true));
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    giveAllPermissionsToRootComponent(view, user, group);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, view.uuid());

    request.setParam(PARAM_PROJECT, view.key())
      .setParam(PARAM_VISIBILITY, PRIVATE)
      .execute();

    verifyStillHasAllPermissions(view, user, group);
  }

  @Test
  public void execute_deletes_all_permissions_USER_and_BROWSE_of_specified_project_when_new_visibility_is_public() {
    ComponentDto project = dbTester.components().insertProject(organization, dto -> dto.setPrivate(true));
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    giveAllPermissionsToRootComponent(project, user, group);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, PUBLIC)
      .execute();

    verifyHasAllPermissionsButProjectPermissionsUserAndBrowse(project, user, group);
  }

  @Test
  public void execute_deletes_all_permissions_USER_and_BROWSE_of_specified_view_when_new_visibility_is_public() {
    ComponentDto view = dbTester.components().insertView(organization, dto -> dto.setPrivate(true));
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    giveAllPermissionsToRootComponent(view, user, group);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, view.uuid());

    request.setParam(PARAM_PROJECT, view.key())
      .setParam(PARAM_VISIBILITY, PUBLIC)
      .execute();

    verifyHasAllPermissionsButProjectPermissionsUserAndBrowse(view, user, group);
  }

  @Test
  public void execute_does_not_delete_permissions_USER_and_BROWSE_of_specified_project_when_new_component_is_already_public() {
    ComponentDto project = dbTester.components().insertProject(organization, dto -> dto.setPrivate(false));
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    giveAllPermissionsToRootComponent(project, user, group);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, PUBLIC)
      .execute();

    verifyStillHasAllPermissions(project, user, group);
  }

  @Test
  public void execute_does_not_delete_permissions_USER_and_BROWSE_of_specified_view_when_new_component_is_already_public() {
    ComponentDto view = dbTester.components().insertView(organization, dto -> dto.setPrivate(false));
    UserDto user = dbTester.users().insertUser();
    GroupDto group = dbTester.users().insertGroup(organization);
    giveAllPermissionsToRootComponent(view, user, group);
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, view.uuid());

    request.setParam(PARAM_PROJECT, view.key())
      .setParam(PARAM_VISIBILITY, PUBLIC)
      .execute();

    verifyStillHasAllPermissions(view, user, group);
  }

  @Test
  public void execute_updates_permission_of_specified_project_in_indexes_when_changing_visibility() {
    boolean initiallyPrivate = random.nextBoolean();
    ComponentDto project = dbTester.components().insertProject(organization, dto -> dto.setPrivate(initiallyPrivate));
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    request.setParam(PARAM_PROJECT, project.key())
      .setParam(PARAM_VISIBILITY, initiallyPrivate ? PUBLIC : PRIVATE)
      .execute();

    verify(permissionIndexer).indexProjectsByUuids(any(DbSession.class), eq(Collections.singletonList(project.uuid())));
  }

  @Test
  public void execute_updates_permission_of_specified_view_in_indexes_when_changing_visibility() {
    boolean initiallyPrivate = random.nextBoolean();
    ComponentDto view = dbTester.components().insertView(organization, dto -> dto.setPrivate(initiallyPrivate));
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, view.uuid());

    request.setParam(PARAM_PROJECT, view.key())
      .setParam(PARAM_VISIBILITY, initiallyPrivate ? PUBLIC : PRIVATE)
      .execute();

    verify(permissionIndexer).indexProjectsByUuids(any(DbSession.class), eq(Collections.singletonList(view.uuid())));
  }

  @Test
  public void execute_does_not_update_permission_of_specified_project_in_indexes_if_already_has_specified_visibility() {
    boolean initiallyPrivate = random.nextBoolean();
    ComponentDto project = dbTester.components().insertProject(organization, dto -> dto.setPrivate(initiallyPrivate));
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    request.setParam(PARAM_PROJECT, project.key())
        .setParam(PARAM_VISIBILITY, initiallyPrivate ? PRIVATE : PUBLIC)
        .execute();

    verifyZeroInteractions(permissionIndexer);
  }

  @Test
  public void execute_does_not_update_permission_of_specified_view_in_indexes_if_already_has_specified_visibility() {
    boolean initiallyPrivate = random.nextBoolean();
    ComponentDto view = dbTester.components().insertView(organization, dto -> dto.setPrivate(initiallyPrivate));
    userSessionRule.addProjectUuidPermissions(UserRole.ADMIN, view.uuid());

    request.setParam(PARAM_PROJECT, view.key())
        .setParam(PARAM_VISIBILITY, initiallyPrivate ? PRIVATE : PUBLIC)
        .execute();

    verifyZeroInteractions(permissionIndexer);
  }

  private void giveAllPermissionsToRootComponent(ComponentDto component, UserDto user, GroupDto group) {
    Arrays.stream(OrganizationPermission.values())
      .forEach(organizationPermission -> {
        dbTester.users().insertPermissionOnAnyone(organization, organizationPermission);
        dbTester.users().insertPermissionOnGroup(group, organizationPermission);
        dbTester.users().insertPermissionOnUser(organization, user, organizationPermission);
      });
    ProjectPermissions.ALL
      .forEach(permission -> {
        dbTester.users().insertProjectPermissionOnAnyone(permission, component);
        dbTester.users().insertProjectPermissionOnGroup(group, permission, component);
        dbTester.users().insertProjectPermissionOnUser(user, permission, component);
      });
  }

  private void verifyHasAllPermissionsButProjectPermissionsToGroupAnyOne(ComponentDto component, UserDto user, GroupDto group) {
    assertThat(dbClient.groupPermissionDao().selectGlobalPermissionsOfGroup(dbSession, organization.getUuid(), null))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.groupPermissionDao().selectGlobalPermissionsOfGroup(dbSession, organization.getUuid(), group.getId()))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.userPermissionDao().selectGlobalPermissionsOfUser(dbSession, user.getId(), organization.getUuid()))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.groupPermissionDao().selectProjectPermissionsOfGroup(dbSession, organization.getUuid(), null, component.getId()))
      .isEmpty();
    assertThat(dbClient.groupPermissionDao().selectProjectPermissionsOfGroup(dbSession, organization.getUuid(), group.getId(), component.getId()))
      .containsAll(ProjectPermissions.ALL);
    assertThat(dbClient.userPermissionDao().selectProjectPermissionsOfUser(dbSession, user.getId(), component.getId()))
      .containsAll(ProjectPermissions.ALL);
  }

  private void verifyHasAllPermissionsButProjectPermissionsUserAndBrowse(ComponentDto component, UserDto user, GroupDto group) {
    assertThat(dbClient.groupPermissionDao().selectGlobalPermissionsOfGroup(dbSession, organization.getUuid(), null))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.groupPermissionDao().selectGlobalPermissionsOfGroup(dbSession, organization.getUuid(), group.getId()))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.userPermissionDao().selectGlobalPermissionsOfUser(dbSession, user.getId(), organization.getUuid()))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.groupPermissionDao().selectProjectPermissionsOfGroup(dbSession, organization.getUuid(), null, component.getId()))
      .doesNotContain(UserRole.USER)
      .doesNotContain(UserRole.CODEVIEWER)
      .containsAll(PROJECT_PERMISSIONS_BUT_USER_AND_CODEVIEWER);
    assertThat(dbClient.groupPermissionDao().selectProjectPermissionsOfGroup(dbSession, organization.getUuid(), group.getId(), component.getId()))
      .doesNotContain(UserRole.USER)
      .doesNotContain(UserRole.CODEVIEWER)
      .containsAll(PROJECT_PERMISSIONS_BUT_USER_AND_CODEVIEWER);
    assertThat(dbClient.userPermissionDao().selectProjectPermissionsOfUser(dbSession, user.getId(), component.getId()))
      .doesNotContain(UserRole.USER)
      .doesNotContain(UserRole.CODEVIEWER)
      .containsAll(PROJECT_PERMISSIONS_BUT_USER_AND_CODEVIEWER);
  }

  private void verifyStillHasAllPermissions(ComponentDto component, UserDto user, GroupDto group) {
    assertThat(dbClient.groupPermissionDao().selectGlobalPermissionsOfGroup(dbSession, organization.getUuid(), null))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.groupPermissionDao().selectGlobalPermissionsOfGroup(dbSession, organization.getUuid(), group.getId()))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.userPermissionDao().selectGlobalPermissionsOfUser(dbSession, user.getId(), organization.getUuid()))
      .containsAll(ORGANIZATION_PERMISSIONS_NAME_SET);
    assertThat(dbClient.groupPermissionDao().selectProjectPermissionsOfGroup(dbSession, organization.getUuid(), null, component.getId()))
      .containsAll(ProjectPermissions.ALL);
    assertThat(dbClient.groupPermissionDao().selectProjectPermissionsOfGroup(dbSession, organization.getUuid(), group.getId(), component.getId()))
      .containsAll(ProjectPermissions.ALL);
    assertThat(dbClient.userPermissionDao().selectProjectPermissionsOfUser(dbSession, user.getId(), component.getId()))
      .containsAll(ProjectPermissions.ALL);
  }

  private void insertPendingTask(ComponentDto project) {
    insertCeQueueDto(project, CeQueueDto.Status.PENDING);
  }

  private void insertInProgressTask(ComponentDto project) {
    insertCeQueueDto(project, CeQueueDto.Status.IN_PROGRESS);
  }

  private int counter = 0;

  private void insertCeQueueDto(ComponentDto project, CeQueueDto.Status status) {
    dbClient.ceQueueDao().insert(dbTester.getSession(), new CeQueueDto()
      .setUuid("pending" + counter++)
      .setComponentUuid(project.uuid())
      .setTaskType("foo")
      .setStatus(status));
    dbTester.commit();
  }

  private void expectInsufficientPrivilegeException() {
    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");
  }

  private boolean isPrivateInDb(ComponentDto project) {
    return dbClient.componentDao().selectByUuid(dbTester.getSession(), project.uuid()).get().isPrivate();
  }
}
