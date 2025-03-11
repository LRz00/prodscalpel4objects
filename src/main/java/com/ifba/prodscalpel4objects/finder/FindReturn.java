package com.ifba.prodscalpel4objects.finder;

import java.util.List;

public record FindReturn(String classOriginPath, String className, List<String> classCallPaths) {

}
