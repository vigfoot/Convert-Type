package com.forestfull.convert_type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConvertTypeTest {

    private void printHeader(String title) {
        System.out.println("\n==================================================");
        System.out.println("🚀 Test: " + title);
        System.out.println("==================================================");
    }

    private void printFooter() {
        System.out.println("✅ Status: PASSED");
        System.out.println("==================================================\n");
    }

    @Test
    @DisplayName("기본 객체 변환 테스트 (Entity -> DTO)")
    void testEntityToDtoConversion() {
        printHeader("기본 객체 변환 (Entity -> DTO)");

        UserEntity entity = new UserEntity("user1", "password123", "John Doe", 30);
        System.out.println("[Source]  " + entity);

        UserDto dto = ConvertType.from(entity).to(UserDto.class);
        System.out.println("[Target]  " + dto);

        assertThat(dto).isNotNull();
        assertThat(dto.username).isEqualTo("user1");
        assertThat(dto.fullName).isEqualTo("John Doe");

        printFooter();
    }

    @Test
    @DisplayName("peek 콜백 테스트 (타입 안전성 및 후처리)")
    void testToWithPeek() {
        printHeader("peek 콜백 (후처리 로직)");

        UserEntity entity = new UserEntity("vigfoot", "1234", "Busan Dev", 35);
        System.out.println("[Source]  " + entity);

        UserDto dto = ConvertType.from(entity).to(UserDto.class, (src, target) -> {
            System.out.println("[Inside Peek] Processing for source: " + src.username);
            target.fullName = src.fullName + " (Verified)";
            target.age = src.age + 5;
        });

        System.out.println("[Target]  " + dto + " (Age modified in peek: " + dto.age + ")");

        assertThat(dto.fullName).contains("(Verified)");
        assertThat(dto.age).isEqualTo(40);

        printFooter();
    }

    @Test
    @DisplayName("중첩 객체 변환 테스트 (Deep Copy)")
    void testNestedObjectConversion() {
        printHeader("중첩 객체 변환 (Order -> OrderDto)");

        ProductEntity product = new ProductEntity("P001", "Laptop", 1500.0);
        OrderEntity order = new OrderEntity("ORD-001", product, 2);
        System.out.println("[Source]  Order ID: " + order.orderId + ", Product: " + order.product.productName);

        OrderDto dto = ConvertType.from(order).to(OrderDto.class);
        System.out.println("[Target]  Order ID: " + dto.orderId + ", Product DTO: " + dto.product.productName);

        assertThat(dto.product).isNotNull();
        assertThat(dto.product.productName).isEqualTo("Laptop");

        printFooter();
    }

    @Test
    @DisplayName("컬렉션 포함 객체 변환 테스트")
    void testCollectionConversion() {
        printHeader("컬렉션 포함 객체 변환 (List<Entity> -> List<Dto>)");

        ProductEntity p1 = new ProductEntity("P001", "Mouse", 20.0);
        ProductEntity p2 = new ProductEntity("P002", "Keyboard", 50.0);
        CategoryEntity category = new CategoryEntity("Electronics", Arrays.asList(p1, p2));
        System.out.println("[Source]  Category: " + category.name + ", Item Count: " + category.products.size());

        CategoryDto dto = ConvertType.from(category).to(CategoryDto.class);
        System.out.println("[Target]  Category: " + dto.name + ", DTO Item Count: " + dto.products.size());

        assertThat(dto.products).hasSize(2);
        assertThat(dto.products.get(0)).isInstanceOf(ProductDto.class);

        printFooter();
    }

    @Test
    @DisplayName("상속 구조 필드 변환 테스트")
    void testInheritanceConversion() {
        printHeader("상속 구조 변환 (Child extends Parent)");

        ChildEntity child = new ChildEntity();
        child.parentField = "Father's Legacy";
        child.childField = "Son's Work";
        System.out.println("[Source]  ParentField: " + child.parentField + ", ChildField: " + child.childField);

        ChildDto dto = ConvertType.from(child).to(ChildDto.class);
        System.out.println("[Target]  ParentField: " + dto.parentField + ", ChildField: " + dto.childField);

        assertThat(dto.parentField).isEqualTo("Father's Legacy");
        assertThat(dto.childField).isEqualTo("Son's Work");

        printFooter();
    }

    @Test
    @DisplayName("순환 참조 방어 테스트 (LIMIT_DEPTH)")
    void testCircularReference() {
        printHeader("순환 참조 방어 (Circular Reference)");

        Node node1 = new Node("Node1");
        Node node2 = new Node("Node2");
        node1.next = node2;
        node2.next = node1;
        System.out.println("[Source]  Created circular link: Node1 <-> Node2");

        NodeDto dto = ConvertType.from(node1).to(NodeDto.class);
        System.out.println("[Target]  Root Name: " + dto.name + " (Stopped by depth limit)");

        assertThat(dto).isNotNull();
        assertThat(dto.name).isEqualTo("Node1");

        printFooter();
    }

    @Test
    @DisplayName("overwrite 기능 테스트")
    void testOverwriteBasic() {
        printHeader("overwrite (null 제외 덮어쓰기)");

        UserEntity original = new UserEntity("user1", "old_pw", "Old Name", 20);
        UserEntity updateSource = new UserEntity(null, "new_pw", null, 30);
        System.out.println("[Original] " + original);
        System.out.println("[Update]   " + updateSource + " (Only password & age provided)");

        UserEntity result = ConvertType.from(original).overwrite(updateSource);
        System.out.println("[Result]   " + result);

        assertThat(result.password).isEqualTo("new_pw");
        assertThat(result.username).isEqualTo("user1"); // null은 덮어쓰지 않음

        printFooter();
    }

    @Test
    @DisplayName("Map -> DTO 변환 테스트")
    void testMapToDtoConversion() {
        printHeader("Map -> DTO 변환");

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("username", "mapUser");
        sourceMap.put("age", 25);
        System.out.println("[Source Map] " + sourceMap);

        UserDto dto = ConvertType.from(sourceMap).to(UserDto.class);
        System.out.println("[Target DTO] " + dto);

        assertThat(dto.username).isEqualTo("mapUser");

        printFooter();
    }

    // --- 테스트용 모델 클래스 (필수) ---

    static class UserEntity {
        String username; String password; String fullName; int age;
        public UserEntity() {}
        public UserEntity(String u, String p, String f, int a) { this.username = u; this.password = p; this.fullName = f; this.age = a; }
        @Override public String toString() { return "UserEntity{username='" + username + "', fullName='" + fullName + "', age=" + age + "}"; }
    }

    static class UserDto {
        String username; String fullName; int age;
        public UserDto() {}
        @Override public String toString() { return "UserDto{username='" + username + "', fullName='" + fullName + "', age=" + age + "}"; }
    }

    static class ProductEntity {
        String productId; String productName; double price;
        public ProductEntity() {}
        public ProductEntity(String id, String n, double p) { this.productId = id; this.productName = n; this.price = p; }
    }

    static class ProductDto {
        String productName; double price;
        public ProductDto() {}
    }

    static class OrderEntity {
        String orderId; ProductEntity product; int quantity;
        public OrderEntity() {}
        public OrderEntity(String id, ProductEntity p, int q) { this.orderId = id; this.product = p; this.quantity = q; }
    }

    static class OrderDto {
        String orderId; ProductDto product; int quantity;
        public OrderDto() {}
    }

    static class CategoryEntity {
        String name; List<ProductEntity> products;
        public CategoryEntity() {}
        public CategoryEntity(String n, List<ProductEntity> p) { this.name = n; this.products = p; }
    }

    static class CategoryDto {
        String name; List<ProductDto> products;
        public CategoryDto() {}
    }

    static class Parent { String parentField; }
    static class ChildEntity extends Parent { String childField; }
    static class ChildDto { String parentField; String childField; }

    static class Node {
        String name; Node next;
        public Node() {}
        public Node(String n) { this.name = n; }
    }
    static class NodeDto {
        String name; NodeDto next;
    }

    static class UserDtoRenamed {
        @ConvertField(mapping = "username") String loginId;
    }

    static class UserDtoIgnored {
        @ConvertField(ignore = true) String username;
        String fullName;
    }
}