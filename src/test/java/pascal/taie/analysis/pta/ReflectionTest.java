/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta;

import org.junit.Test;
import pascal.taie.analysis.Tests;

public class ReflectionTest {

    private static final String DIR = "reflection";

    @Test
    public void testStringConstant() {
        Tests.testPTA(DIR, "GetMember");
    }

    @Test
    public void testReflectionLog() {
        Tests.testPTA(DIR, "ReflectiveAction",
                "reflection-inference:null",
                "reflection-log:src/test/resources/pta/reflection/ReflectiveAction.log");
    }

    // Test cases for Solar
    @Test
    public void testLazyHeapModeling() {
        Tests.testPTA(DIR, "LazyHeapModeling", "reflection-inference:solar");
    }

    @Test
    public void testArgsRefine() {
        Tests.testPTA(DIR, "ArgsRefine", "reflection-inference:solar");
    }

    @Test
    public void testGetMethods() {
        Tests.testPTA(DIR, "GetMethods", "reflection-inference:solar");
    }

    @Test
    public void testUnknownMethodName() {
        Tests.testPTA(DIR, "UnknownMethodName", "reflection-inference:solar");
    }
}
