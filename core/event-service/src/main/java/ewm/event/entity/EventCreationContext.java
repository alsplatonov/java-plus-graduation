package ewm.event.entity;

import ewm.category.Category;

public record EventCreationContext(Category category, Long initiatorId) {
}
