package ewm.event.repository;

import ewm.event.entity.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Выбирает только ключи событий (id и лимит участников) по спецификации в порядке даты события
 * Нужен для сортировки по показателям из других сервисов (просмотры, рейтинг) и фильтра onlyAvailable
 * до применения пагинации
 */
@Repository
public class EventKeyFinder {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Object[]> findKeys(Specification<Event> specification) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = builder.createQuery(Object[].class);
        Root<Event> root = query.from(Event.class);
        query.multiselect(root.get("id"), root.get("participantLimit"))
                .where(specification.toPredicate(root, query, builder))
                .orderBy(builder.asc(root.get("eventDate")), builder.asc(root.get("id")));
        return entityManager.createQuery(query).getResultList();
    }
}
