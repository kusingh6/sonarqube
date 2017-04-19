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
// @flow
import React from 'react';
import Helmet from 'react-helmet';
import key from 'keymaster';
import { keyBy, without } from 'lodash';
import HeaderPanel from './HeaderPanel';
import PageActions from './PageActions';
import FiltersHeader from './FiltersHeader';
import MyIssuesFilter from './MyIssuesFilter';
import Sidebar from '../sidebar/Sidebar';
import IssuesList from './IssuesList';
import ComponentBreadcrumbs from './ComponentBreadcrumbs';
import IssuesSourceViewer from './IssuesSourceViewer';
import BulkChangeModal from './BulkChangeModal';
import ConciseIssuesList from '../conciseIssuesList/ConciseIssuesList';
import ConciseIssuesListHeader from '../conciseIssuesList/ConciseIssuesListHeader';
import {
  parseQuery,
  areMyIssuesSelected,
  areQueriesEqual,
  getOpen,
  serializeQuery,
  parseFacets
} from '../utils';
import type {
  Query,
  Paging,
  Facet,
  ReferencedComponent,
  ReferencedUser,
  ReferencedLanguage,
  Component,
  CurrentUser
} from '../utils';
import ListFooter from '../../../components/controls/ListFooter';
import EmptySearch from '../../../components/common/EmptySearch';
import Page from '../../../components/layout/Page';
import PageMain from '../../../components/layout/PageMain';
import PageMainInner from '../../../components/layout/PageMainInner';
import PageSide from '../../../components/layout/PageSide';
import PageFilters from '../../../components/layout/PageFilters';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { scrollToElement } from '../../../helpers/scrolling';
import type { Issue } from '../../../components/issue/types';
import '../styles.css';

type Props = {
  component?: Component,
  currentUser: CurrentUser,
  fetchIssues: () => Promise<*>,
  location: { pathname: string, query: { [string]: string } },
  onRequestFail: (Error) => void,
  router: { push: () => void, replace: () => void }
};

type State = {
  bulkChange: "all" | "selected" | null,
  checked: Array<string>,
  facets: { [string]: Facet },
  issues: Array<Issue>,
  loading: boolean,
  myIssues: boolean,
  openFacets: { [string]: boolean },
  paging?: Paging,
  query: Query,
  referencedComponents: { [string]: ReferencedComponent },
  referencedLanguages: { [string]: ReferencedLanguage },
  referencedRules: { [string]: { name: string } },
  referencedUsers: { [string]: ReferencedUser },
  selected?: string
};

const DEFAULT_QUERY = { resolved: 'false' };

export default class App extends React.PureComponent {
  mounted: boolean;
  props: Props;
  state: State;

  constructor(props: Props) {
    super(props);
    this.state = {
      bulkChange: null,
      checked: [],
      facets: {},
      issues: [],
      loading: true,
      myIssues: areMyIssuesSelected(props.location.query),
      openFacets: { resolutions: true, types: true },
      query: parseQuery(props.location.query),
      referencedComponents: {},
      referencedLanguages: {},
      referencedRules: {},
      referencedUsers: {},
      selected: getOpen(props.location.query)
    };
  }

  componentDidMount() {
    this.mounted = true;

    const footer = document.getElementById('footer');
    if (footer) {
      footer.classList.add('search-navigator-footer');
    }

    this.attachShortcuts();
    this.fetchFirstIssues();
  }

  componentWillReceiveProps(nextProps: Props) {
    const open = getOpen(nextProps.location.query);
    if (open != null && open !== this.state.selected) {
      this.setState({ selected: open });
    }
    this.setState({
      myIssues: areMyIssuesSelected(nextProps.location.query),
      query: parseQuery(nextProps.location.query)
    });
  }

  componentDidUpdate(prevProps: Props, prevState: State) {
    const { query } = this.props.location;
    const { query: prevQuery } = prevProps.location;
    if (
      !areQueriesEqual(prevQuery, query) ||
      areMyIssuesSelected(prevQuery) !== areMyIssuesSelected(query)
    ) {
      this.fetchFirstIssues();
    } else if (prevState.selected !== this.state.selected) {
      const open = getOpen(query);
      if (!open) {
        this.scrollToSelectedIssue();
      }
    }
  }

  componentWillUnmount() {
    this.detachShortcuts();

    const footer = document.getElementById('footer');
    if (footer) {
      footer.classList.remove('search-navigator-footer');
    }

    this.mounted = false;
  }

  attachShortcuts() {
    key.setScope('issues');
    key('up', 'issues', () => {
      this.selectPreviousIssue();
      return false;
    });
    key('down', 'issues', () => {
      this.selectNextIssue();
      return false;
    });
    key('right', 'issues', () => {
      this.openSelectedIssue();
      return false;
    });
    key('left', 'issues', () => {
      this.closeIssue();
      return false;
    });
  }

  detachShortcuts() {
    key.deleteScope('issues');
  }

  getSelectedIndex(): ?number {
    const { issues, selected } = this.state;
    const index = issues.findIndex(issue => issue.key === selected);
    return index !== -1 ? index : null;
  }

  selectNextIssue = () => {
    const { issues } = this.state;
    const selectedIndex = this.getSelectedIndex();
    if (issues != null && selectedIndex != null && selectedIndex < issues.length - 1) {
      if (getOpen(this.props.location.query)) {
        this.openIssue(issues[selectedIndex + 1].key);
      } else {
        this.setState({ selected: issues[selectedIndex + 1].key });
      }
    }
  };

  selectPreviousIssue = () => {
    const { issues } = this.state;
    const selectedIndex = this.getSelectedIndex();
    if (issues != null && selectedIndex != null && selectedIndex > 0) {
      if (getOpen(this.props.location.query)) {
        this.openIssue(issues[selectedIndex - 1].key);
      } else {
        this.setState({ selected: issues[selectedIndex - 1].key });
      }
    }
  };

  openIssue = (issue: string) => {
    const path = {
      pathname: this.props.location.pathname,
      query: {
        ...serializeQuery(this.state.query),
        id: this.props.component && this.props.component.key,
        myIssues: this.state.myIssues ? 'true' : undefined,
        open: issue
      }
    };
    const open = getOpen(this.props.location.query);
    if (open) {
      this.props.router.replace(path);
    } else {
      this.props.router.push(path);
    }
  };

  closeIssue = () => {
    if (this.state.query) {
      this.props.router.push({
        pathname: this.props.location.pathname,
        query: {
          ...serializeQuery(this.state.query),
          id: this.props.component && this.props.component.key,
          myIssues: this.state.myIssues ? 'true' : undefined,
          open: undefined
        }
      });
    }
  };

  openSelectedIssue = () => {
    const { selected } = this.state;
    if (selected) {
      this.openIssue(selected);
    }
  };

  scrollToSelectedIssue = () => {
    const { selected } = this.state;
    if (selected) {
      const element = document.querySelector(`[data-issue="${selected}"]`);
      if (element) {
        scrollToElement(element, 150, 100);
      }
    }
  };

  fetchIssues = (additional?: {}, requestFacets?: boolean = false): Promise<*> => {
    const { component } = this.props;
    const { myIssues, query } = this.state;

    const parameters = {
      componentKeys: component && component.key,
      ...serializeQuery(query),
      s: 'FILE_LINE',
      ps: 25,
      facets: requestFacets
        ? [
            'assignees',
            'authors',
            'createdAt',
            'directories',
            'fileUuids',
            'languages',
            'moduleUuids',
            'projectUuids',
            'resolutions',
            'rules',
            'severities',
            'statuses',
            'tags',
            'types'
          ].join()
        : undefined,
      ...additional
    };

    if (myIssues) {
      Object.assign(parameters, { assignees: '__me__' });
    }

    return this.props.fetchIssues(parameters);
  };

  fetchFirstIssues() {
    this.setState({ loading: true });
    return this.fetchIssues({}, true).then(({ facets, issues, paging, ...other }) => {
      if (this.mounted) {
        const open = getOpen(this.props.location.query);
        this.setState({
          facets: parseFacets(facets),
          loading: false,
          issues,
          paging,
          referencedComponents: keyBy(other.components, 'uuid'),
          referencedLanguages: keyBy(other.languages, 'key'),
          referencedRules: keyBy(other.rules, 'key'),
          referencedUsers: keyBy(other.users, 'login'),
          selected: issues.length > 0
            ? issues.find(issue => issue.key === open) != null ? open : issues[0].key
            : undefined
        });
      }
      return issues;
    });
  }

  fetchIssuesPage = (p: number): Promise<*> => {
    return this.fetchIssues({ p });
  };

  fetchIssuesUntil = (p: number, done: (Array<Issue>, Paging) => boolean) => {
    return this.fetchIssuesPage(p).then(response => {
      const { issues, paging } = response;

      return done(issues, paging)
        ? { issues, paging }
        : this.fetchIssuesUntil(p + 1, done).then(nextResponse => {
            return {
              issues: [...issues, ...nextResponse.issues],
              paging: nextResponse.paging
            };
          });
    });
  };

  fetchMoreIssues = () => {
    const { paging } = this.state;

    if (!paging) {
      return;
    }

    const p = paging.pageIndex + 1;

    this.setState({ loading: true });
    this.fetchIssuesPage(p).then(response => {
      if (this.mounted) {
        this.setState(state => ({
          loading: false,
          issues: [...state.issues, ...response.issues],
          paging: response.paging
        }));
      }
    });
  };

  fetchIssuesForComponent = (): Promise<Array<Issue>> => {
    const { issues, paging } = this.state;

    const open = getOpen(this.props.location.query);
    const openIssue = issues.find(issue => issue.key === open);

    if (!openIssue || !paging) {
      return Promise.reject();
    }

    const isSameComponent = (issue: Issue): boolean => issue.component === openIssue.component;

    const done = (issues: Array<Issue>, paging: Paging): boolean =>
      paging.total <= paging.pageIndex * paging.pageSize ||
      issues[issues.length - 1].component !== openIssue.component;

    if (done(issues, paging)) {
      return Promise.resolve(issues.filter(isSameComponent));
    }

    this.setState({ loading: true });
    return this.fetchIssuesUntil(paging.pageIndex + 1, done).then(response => {
      const nextIssues = [...issues, ...response.issues];

      this.setState({
        issues: nextIssues,
        loading: false,
        paging: response.paging
      });
      return nextIssues.filter(isSameComponent);
    });
  };

  isFiltered = () => {
    const serialized = serializeQuery(this.state.query);
    return !areQueriesEqual(serialized, DEFAULT_QUERY);
  };

  getCheckedIssues = () => {
    const issues = this.state.checked.map(checked =>
      this.state.issues.find(issue => issue.key === checked));
    const paging = { pageIndex: 1, pageSize: issues.length, total: issues.length };
    return Promise.resolve({ issues, paging });
  };

  handleFilterChange = (changes: {}) => {
    this.props.router.push({
      pathname: this.props.location.pathname,
      query: {
        ...serializeQuery({ ...this.state.query, ...changes }),
        id: this.props.component && this.props.component.key,
        myIssues: this.state.myIssues ? 'true' : undefined
      }
    });
  };

  handleMyIssuesChange = (myIssues: boolean) => {
    this.closeFacet('assignees');
    this.props.router.push({
      pathname: this.props.location.pathname,
      query: {
        ...serializeQuery({ ...this.state.query, assigned: true, assignees: [] }),
        id: this.props.component && this.props.component.key,
        myIssues: myIssues ? 'true' : undefined
      }
    });
  };

  closeFacet = (property: string) => {
    this.setState(state => ({
      openFacets: { ...state.openFacets, [property]: false }
    }));
  };

  handleFacetToggle = (property: string) => {
    this.setState(state => ({
      openFacets: { ...state.openFacets, [property]: !state.openFacets[property] }
    }));
  };

  handleReset = () => {
    this.props.router.push({
      pathname: this.props.location.pathname,
      query: {
        ...DEFAULT_QUERY,
        id: this.props.component && this.props.component.key,
        myIssues: this.state.myIssues ? 'true' : undefined
      }
    });
  };

  handleIssueCheck = (issue: string) => {
    this.setState(state => ({
      checked: state.checked.includes(issue)
        ? without(state.checked, issue)
        : [...state.checked, issue]
    }));
  };

  handleIssueChange = (issue: Issue) => {
    this.setState(state => ({
      issues: state.issues.map(candidate => candidate.key === issue.key ? issue : candidate)
    }));
  };

  openBulkChange = (mode: "all" | "selected") => {
    this.setState({ bulkChange: mode });
    key.setScope('issues-bulk-change');
  };

  closeBulkChange = () => {
    key.setScope('issues');
    this.setState({ bulkChange: null });
  };

  handleBulkChangeClick = (e: Event & { target: HTMLElement }) => {
    e.preventDefault();
    e.target.blur();
    this.openBulkChange('all');
  };

  handleBulkChangeSelectedClick = (e: Event & { target: HTMLElement }) => {
    e.preventDefault();
    e.target.blur();
    this.openBulkChange('selected');
  };

  handleBulkChangeDone = () => {
    this.fetchFirstIssues();
    this.closeBulkChange();
  };

  handleReloadAndOpenFirst = () => {
    this.fetchFirstIssues().then(issues => {
      if (issues.length > 0) {
        this.openIssue(issues[0].key);
      }
    });
  };

  renderBulkChange(openIssue?: Issue) {
    const { component, currentUser } = this.props;
    const { bulkChange, checked, paging } = this.state;

    if (!currentUser.isLoggedIn || openIssue != null) {
      return null;
    }

    return (
      <div className="pull-left">
        {checked.length > 0
          ? <div className="dropdown">
              <button id="issues-bulk-change" data-toggle="dropdown">
                {translate('bulk_change')}
                <i className="icon-dropdown little-spacer-left" />
              </button>
              <ul className="dropdown-menu">
                <li>
                  <a href="#" onClick={this.handleBulkChangeClick}>
                    {translateWithParameters('issues.bulk_change', paging ? paging.total : 0)}
                  </a>
                </li>
                <li>
                  <a href="#" onClick={this.handleBulkChangeSelectedClick}>
                    {translateWithParameters('issues.bulk_change_selected', checked.length)}
                  </a>
                </li>
              </ul>
            </div>
          : <button id="issues-bulk-change" onClick={this.handleBulkChangeClick}>
              {translate('bulk_change')}
            </button>}
        {bulkChange != null &&
          <BulkChangeModal
            component={component}
            currentUser={currentUser}
            fetchIssues={bulkChange === 'all' ? this.fetchIssues : this.getCheckedIssues}
            onClose={this.closeBulkChange}
            onDone={this.handleBulkChangeDone}
            onRequestFail={this.props.onRequestFail}
          />}
      </div>
    );
  }

  renderFacets() {
    const { component, currentUser } = this.props;
    const { query } = this.state;

    return (
      <PageFilters>
        {currentUser.isLoggedIn &&
          <MyIssuesFilter
            myIssues={this.state.myIssues}
            onMyIssuesChange={this.handleMyIssuesChange}
          />}
        <FiltersHeader displayReset={this.isFiltered()} onReset={this.handleReset} />
        <Sidebar
          component={component}
          facets={this.state.facets}
          myIssues={this.state.myIssues}
          onFacetToggle={this.handleFacetToggle}
          onFilterChange={this.handleFilterChange}
          openFacets={this.state.openFacets}
          query={query}
          referencedComponents={this.state.referencedComponents}
          referencedLanguages={this.state.referencedLanguages}
          referencedRules={this.state.referencedRules}
          referencedUsers={this.state.referencedUsers}
        />
      </PageFilters>
    );
  }

  renderConciseIssuesList() {
    const { issues, paging } = this.state;

    return (
      <PageFilters>
        <ConciseIssuesListHeader
          loading={this.state.loading}
          onBackClick={this.closeIssue}
          onReload={this.handleReloadAndOpenFirst}
          paging={this.state.paging}
          selectedIndex={this.getSelectedIndex()}
        />
        <ConciseIssuesList
          issues={this.state.issues}
          onIssueSelect={this.openIssue}
          selected={this.state.selected}
        />
        {paging != null &&
          paging.total > 0 &&
          <ListFooter total={paging.total} count={issues.length} loadMore={this.fetchMoreIssues} />}
      </PageFilters>
    );
  }

  renderSide() {
    const open = getOpen(this.props.location.query);
    const openIssue = this.state.issues.find(issue => issue.key === open);

    const top = this.props.component ? 95 : 30;

    return (
      <PageSide top={top}>
        {openIssue == null ? this.renderFacets() : this.renderConciseIssuesList()}
      </PageSide>
    );
  }

  renderList(openIssue?: Issue) {
    const { component, currentUser } = this.props;
    const { issues, paging } = this.state;
    const selectedIndex = this.getSelectedIndex();
    const selectedIssue = selectedIndex != null ? issues[selectedIndex] : null;

    if (paging == null) {
      return null;
    }

    return (
      <div className={openIssue != null ? 'hidden' : undefined}>
        {paging.total > 0 &&
          <IssuesList
            checked={this.state.checked}
            component={component}
            issues={issues}
            onFilterChange={this.handleFilterChange}
            onIssueChange={this.handleIssueChange}
            onIssueCheck={currentUser.isLoggedIn ? this.handleIssueCheck : undefined}
            onIssueClick={this.openIssue}
            selectedIssue={selectedIssue}
          />}

        {paging.total > 0 &&
          <ListFooter total={paging.total} count={issues.length} loadMore={this.fetchMoreIssues} />}

        {paging.total === 0 && <EmptySearch />}
      </div>
    );
  }

  render() {
    const { component } = this.props;
    const { issues, paging } = this.state;

    const open = getOpen(this.props.location.query);
    const openIssue = issues.find(issue => issue.key === open);

    const selectedIndex = this.getSelectedIndex();

    const top = component ? 95 : 30;

    return (
      <Page className="issues" id="issues-page">
        <Helmet title={translate('issues.page')} titleTemplate="%s - SonarQube" />

        {this.renderSide()}

        <PageMain>
          <HeaderPanel border={true} top={top}>
            <PageMainInner>
              {this.renderBulkChange(openIssue)}
              {openIssue != null
                ? <div className="pull-left">
                    <ComponentBreadcrumbs component={component} issue={openIssue} />
                  </div>
                : <PageActions
                    loading={this.state.loading}
                    paging={paging}
                    selectedIndex={selectedIndex}
                  />}
            </PageMainInner>
          </HeaderPanel>

          <PageMainInner>
            <div>
              {openIssue != null &&
                <IssuesSourceViewer
                  openIssue={openIssue}
                  loadIssues={this.fetchIssuesForComponent}
                  onIssueChange={this.handleIssueChange}
                  onIssueSelect={this.openIssue}
                />}

              {this.renderList(openIssue)}
            </div>
          </PageMainInner>
        </PageMain>
      </Page>
    );
  }
}
