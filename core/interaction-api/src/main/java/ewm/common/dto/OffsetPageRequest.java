package ewm.common.dto;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pageable с произвольным смещением (from), не обязательно кратным размеру страницы
 * PageRequest.of(from / size, size) при from=5, size=10 вернёт записи с нулевой позиции
 */
public final class OffsetPageRequest implements Pageable {

    private final long offset;
    private final int size;
    private final Sort sort;

    private OffsetPageRequest(long offset, int size, Sort sort) {
        if (offset < 0) {
            throw new IllegalArgumentException("Смещение не может быть отрицательным");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Размер страницы должен быть больше нуля");
        }
        this.offset = offset;
        this.size = size;
        this.sort = sort;
    }

    public static OffsetPageRequest of(int from, int size) {
        return new OffsetPageRequest(from, size, Sort.unsorted());
    }

    public static OffsetPageRequest of(int from, int size, Sort sort) {
        return new OffsetPageRequest(from, size, sort);
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / size);
    }

    @Override
    public int getPageSize() {
        return size;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageRequest(offset + size, size, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetPageRequest(offset - size, size, sort) : first();
    }

    @Override
    public Pageable first() {
        return new OffsetPageRequest(0, size, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageRequest((long) pageNumber * size, size, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset >= size;
    }
}
