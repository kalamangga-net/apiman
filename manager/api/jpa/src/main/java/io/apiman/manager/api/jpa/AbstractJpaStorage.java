/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apiman.manager.api.jpa;

import io.apiman.common.logging.ApimanLoggerFactory;
import io.apiman.common.logging.IApimanLogger;
import io.apiman.manager.api.beans.orgs.OrganizationBasedCompositeId;
import io.apiman.manager.api.beans.orgs.OrganizationBean;
import io.apiman.manager.api.beans.search.OrderByBean;
import io.apiman.manager.api.beans.search.PagingBean;
import io.apiman.manager.api.beans.search.SearchCriteriaBean;
import io.apiman.manager.api.beans.search.SearchCriteriaFilterBean;
import io.apiman.manager.api.beans.search.SearchCriteriaFilterOperator;
import io.apiman.manager.api.beans.search.SearchResultsBean;
import io.apiman.manager.api.core.config.ApiManagerConfig;
import io.apiman.manager.api.core.exceptions.StorageException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.sql.DataSource;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.PagedList;
import com.blazebit.persistence.PaginatedCriteriaBuilder;
import com.blazebit.persistence.Path;
import org.hibernate.Session;
import org.jdbi.v3.core.Jdbi;

/**
 * A base class that JPA storage impls can extend.
 *
 * @author eric.wittmann@redhat.com
 */
public abstract class AbstractJpaStorage {

    private static final IApimanLogger LOGGER = ApimanLoggerFactory.getLogger(AbstractJpaStorage.class);

    @Inject
    private EntityManagerFactoryAccessor emf;

    @Inject
    private ApiManagerConfig config;

    @Inject
    private CriteriaBuilderFactory criteriaBuilderFactory;

    /**
     * Constructor.
     */
    public AbstractJpaStorage() {
    }

    protected Jdbi getJdbi() {
        return Jdbi.create(lookupDS(config.getHibernateDataSource()));
    }

    protected CriteriaBuilderFactory getCriteriaBuilderFactory() {
        return criteriaBuilderFactory;
    }

    /**
     * @return the thread's entity manager
     */
    public EntityManager getActiveEntityManager() {
        return emf.getEntityManager();
    }

    public Session getSession() {
        return getActiveEntityManager().unwrap(Session.class);
    }

    private static DataSource lookupDS(String dsJndiLocation) {
        DataSource ds;
        try {
            InitialContext ctx = new InitialContext();
            ds = (DataSource) ctx.lookup(dsJndiLocation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (ds == null) {
            throw new RuntimeException("Datasource not found: " + dsJndiLocation); //$NON-NLS-1$
        }
        return ds;
    }

    /**
     * @param bean the bean to create
     * @throws StorageException if a storage problem occurs while storing a bean
     */
    public <T> void create(T bean) throws StorageException {
        if (bean == null) {
            return;
        }
        EntityManager entityManager = getActiveEntityManager();
        try {
            entityManager.persist(bean);
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
            throw new StorageException(t);
        }
    }

    /**
     * @param bean the bean to update
     * @throws StorageException if a storage problem occurs while storing a bean
     */
    public <T> void update(T bean) throws StorageException {
        EntityManager entityManager = getActiveEntityManager();
        try {
            if (!entityManager.contains(bean)) {
                entityManager.merge(bean);
            }
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
            throw new StorageException(t);
        }
    }

    /**
     * Delete using bean
     *
     * @param bean the bean to delete
     * @throws StorageException if a storage problem occurs while storing a bean
     */
    public <T> void delete(T bean) throws StorageException {
        EntityManager entityManager = getActiveEntityManager();
        try {
            //entityManager.merge(bean);
            entityManager.remove(bean);
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
            throw new StorageException(t);
        }
    }

    /**
     * Get object of type T
     *
     * @param id identity key
     * @param type class of type T
     * @return Instance of type T
     * @throws StorageException if a storage problem occurs while storing a bean
     */
    public <T> T get(Long id, Class<T> type) throws StorageException {
        T rval;
        EntityManager entityManager = getActiveEntityManager();
        try {
            rval = entityManager.find(type, id);
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
            throw new StorageException(t);
        }
        return rval;
    }

    /**
     * Get object of type T
     *
     * @param id identity key
     * @param type class of type T
     * @return Instance of type T
     * @throws StorageException if a storage problem occurs while storing a bean
     */
    public <T> T get(String id, Class<T> type) throws StorageException {
        T rval;
        EntityManager entityManager = getActiveEntityManager();
        try {
            rval = entityManager.find(type, id);
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
            throw new StorageException(t);
        }
        return rval;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected <T> Iterator<T> getAll(Class<T> type, Query query) throws StorageException {
        return new EntityIterator(type, query);
    }

    /**
     * Get object of type T
     *
     * @param organizationId org id
     * @param id identity
     * @param type class of type T
     * @return Instance of type T
     * @throws StorageException if a storage problem occurs while storing a bean
     */
    public <T> T get(String organizationId, String id, Class<T> type) throws StorageException {
        try {
            EntityManager entityManager = getActiveEntityManager();
            OrganizationBean orgBean = entityManager.find(OrganizationBean.class, organizationId);
            Object key = new OrganizationBasedCompositeId(orgBean, id);
            return entityManager.find(type, key);
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
            throw new StorageException(t);
        }
    }

    protected <T> SearchResultsBean<T> find(SearchCriteriaBean criteria, Set<OrderByBean> uniqueOrderIdentifiers, Class<T> type, boolean paginate) throws StorageException {
        return find(criteria, uniqueOrderIdentifiers, (criteriaBuilder) -> {}, type, type.getSimpleName(), paginate);
    }

    /**
     * Get a list of entities based on the provided criteria and entity type.
     * @param criteria
     * @param type
     * @throws StorageException if a storage problem occurs while storing a bean
     */
    protected <T> SearchResultsBean<T> find(SearchCriteriaBean criteria,
                                            Set<OrderByBean> uniqueOrderIdentifiers,
                                            Consumer<CriteriaBuilder<T>> builderCallback,
                                            Class<T> type,
                                            String typeAlias,
                                            boolean paginate) throws StorageException {
        try {
            // Set some default in the case that paging information was not included in the request.
            PagingBean paging = criteria.getPaging();
            if (paging == null) {
                paging = PagingBean.create(1, 20);
            }
            int page = paging.getPage();
            int pageSize = paging.getPageSize();
            int start = (page - 1) * pageSize;

            CriteriaBuilder<T> cb = criteriaBuilderFactory
                            .create(getActiveEntityManager(), type)
                            .from(type, typeAlias);

            // Apply filters from user-provided criteria.
            cb = applySearchCriteriaToQuery(typeAlias, criteria, cb, false);

            // Allow caller to modify the query, for example to add permissions constraints.
            builderCallback.accept(cb);

            if (paginate) {
                PaginatedCriteriaBuilder<T> paginatedCb = cb.page(start, pageSize);
                /*
                 * Add an orderBy of unique identifiers *last* in the query; this is required for pagination to work properly.
                 *
                 * The tuple formed by the fields in this orderBy clause MUST be unique, otherwise BlazePersistence will throw an exception.
                 *
                 * Without a unique tuple, the ordering may be unstable, which can cause pagination to behave unpredictably.
                 */
                for (OrderByBean uniqueOrder : uniqueOrderIdentifiers) {
                    if (!duplicateOrderBy(typeAlias, uniqueOrder, criteria.getOrderBy())) {
                        paginatedCb = paginatedCb.orderBy(uniqueOrder.getName(), uniqueOrder.isAscending());
                    }
                }

                PagedList<T> resultList = paginatedCb.getResultList();

                return new SearchResultsBean<T>()
                        .setTotalSize(Math.toIntExact(resultList.getTotalSize()))
                        .setBeans(resultList);
            } else {
                // Pagination sometimes generates #in SQL statements that H2 currently does not support due to composite key
                //    (x,y) IN (select x,y ... subquery) which works in all DBs except H2. Beware...
                for (OrderByBean order : uniqueOrderIdentifiers) {
                    cb = cb.orderBy(order.getName(), order.isAscending());
                }
                List<T> resultList = cb.getResultList();
                return new SearchResultsBean<T>()
                        .setTotalSize(Math.toIntExact(resultList.size()))
                        .setBeans(resultList);
            }
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
            throw new StorageException(t);
        }
    }

    /**
     * MSSQL issue: if the user provides a sort order that is also in our default sort order list,  size = 2
     * we may end up with the duplicate "orderBy" clauses, which MSSQL rejects. All other DBs don't seem to care...
     */
    private boolean duplicateOrderBy(String typeAlias, OrderByBean defaultOrderEntry, OrderByBean userOrder) {
        Objects.requireNonNull(typeAlias);
        Objects.requireNonNull(defaultOrderEntry);
        if (userOrder == null || userOrder.getName() == null) {
            return false;
        }
        return defaultOrderEntry.getName().equalsIgnoreCase(userOrder.getName()) ||
                // Catch cases like "api.foo" vs "foo" where "foo" is implicitly referring to same entity.
                defaultOrderEntry.getName().equalsIgnoreCase(typeAlias + "." + userOrder.getName());
    }

    /**
     * Applies the criteria found in the {@link SearchCriteriaBean} to the JPA query.
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected <T> CriteriaBuilder<T> applySearchCriteriaToQuery(String rootAlias, SearchCriteriaBean criteria, CriteriaBuilder<T> cb, boolean countOnly) {
        List<SearchCriteriaFilterBean> filters = criteria.getFilters();
        if (filters != null && !filters.isEmpty()) {
            for (SearchCriteriaFilterBean filter : filters) {
                Path path = cb.getPath(filter.getName());
                Class<?> pathKlazz = path.getJavaType();
                final String name = cb.getPath(filter.getName()).getPath();
                if (filter.getOperator() == SearchCriteriaFilterOperator.eq) {
                    if (pathKlazz.isEnum()) {
                        cb = cb.where(name).eq(Enum.valueOf((Class) pathKlazz, filter.getValue()));
                    } else {
                        cb = cb.where(name).eq(filter.getValue());
                    }
                } else if (filter.getOperator() == SearchCriteriaFilterOperator.bool_eq) {
                    cb = cb.where(name).eq(Boolean.valueOf(filter.getValue()));
                } else if (filter.getOperator() == SearchCriteriaFilterOperator.gt) {
                    cb = cb.where(name).gt(numberValueOf(pathKlazz, filter.getValue()));
                } else if (filter.getOperator() == SearchCriteriaFilterOperator.gte) {
                    cb = cb.where(name).ge(numberValueOf(pathKlazz, filter.getValue()));
                } else if (filter.getOperator() == SearchCriteriaFilterOperator.lt) {
                    cb = cb.where(name).lt(numberValueOf(pathKlazz, filter.getValue()));
                } else if (filter.getOperator() == SearchCriteriaFilterOperator.lte) {
                    cb = cb.where(name).le(numberValueOf(pathKlazz, filter.getValue()));
                } else if (filter.getOperator() == SearchCriteriaFilterOperator.neq) {
                    cb = cb.where(name).notEq(numberValueOf(pathKlazz, filter.getValue()));
                } else if (filter.getOperator() == SearchCriteriaFilterOperator.like) {
                    cb = cb.where(name).like(false).value(filter.getValue().toUpperCase().replace('*', '%')).noEscape();
                }
            }
        }

        OrderByBean orderBy = criteria.getOrderBy();
        if (orderBy != null && !countOnly) {
            if (orderBy.isAscending()) {
                cb = cb.orderByAsc(orderBy.getName());
            } else {
                cb = cb.orderByDesc(orderBy.getName());
            }
        }

        return cb;
    }

    private Number numberValueOf(Class<?> klazz, String value) {
        if (klazz.equals(long.class) || klazz.equals(Long.class)) {
            return Long.valueOf(value);
        }
        else if (klazz.equals(int.class) || klazz.equals(Integer.class)) {
            return Integer.valueOf(value);
        }
        else if (klazz.equals(byte.class) || klazz.equals(Byte.class)) {
            return Byte.valueOf(value);
        }
        throw new IllegalArgumentException("This method accepts only Integer/int, Long/long, or Byte/byte");
    }

    @SuppressWarnings("unchecked")
    protected <T> Optional<T> getOne(TypedQuery<T> query) {
        List<T> resultList = (List<T>) query.getResultList();

        if (resultList.size() > 1) {
            throw new IllegalStateException("More than one result for query");
        }

        if (resultList.size() == 0) {
            return Optional.empty();
        }

        return Optional.of(resultList.get(0));
    }


    /**
     * Allows iterating over all entities of a given type.
     * @author eric.wittmann@redhat.com
     */
    private static class EntityIterator<T> implements Iterator<T> {

        private Query query;
        private int pageIndex = 0;
        private int pageSize = 100;

        private int resultIndex;
        private List<T> results;

        /**
         * Constructor.
         * @param query the query
         * @throws StorageException if a storage problem occurs while storing a bean.
         */
        public EntityIterator(Class<T> type, Query query) throws StorageException {
            this.query = query;
            fetch();
        }

        /**
         * Initialize the search.
         */
        private void fetch() {
            if (results != null && results.size() < pageSize) {
                results = new ArrayList<>();
            } else {
                query.setFirstResult(pageIndex);
                query.setMaxResults(pageSize);
                results = query.getResultList();
            }
            resultIndex = 0;
            pageIndex += pageSize;
        }

        /**
         * @see java.util.Iterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            return resultIndex < results.size();
        }

        /**
         * @see java.util.Iterator#next()
         */
        @Override
        public T next() {
            T rval = results.get(resultIndex++);
            if (resultIndex >= results.size()) {
                fetch();
            }
            return rval;
        }

        /**
         * @see java.util.Iterator#remove()
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

}
