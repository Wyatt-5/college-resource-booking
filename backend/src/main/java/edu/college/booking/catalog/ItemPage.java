package edu.college.booking.catalog;

import java.util.List;

public record ItemPage(List<Item> content, int page, int size, long totalElements) {}
