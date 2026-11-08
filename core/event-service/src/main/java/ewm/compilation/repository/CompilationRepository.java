package ewm.compilation.repository;

import ewm.compilation.entity.Compilation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompilationRepository extends JpaRepository<Compilation, Long> {

    // Идентификаторы подборок запрошенной страницы
    @Query("SELECT c.id FROM Compilation c "
            + "WHERE (:pinned IS NULL OR c.pinned = :pinned) "
            + "ORDER BY c.id")
    List<Long> findIds(@Param("pinned") Boolean pinned, Pageable pageable);

    // Подборки вместе с событиями и их категориями одним запросом (без N+1)
    @Query("SELECT DISTINCT c FROM Compilation c "
            + "LEFT JOIN FETCH c.events e "
            + "LEFT JOIN FETCH e.category "
            + "WHERE c.id IN :ids")
    List<Compilation> findAllWithEvents(@Param("ids") List<Long> ids);
}
