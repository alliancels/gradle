#include <unity.h>
#include <unity_fixture.h>
#include "operators.h"

TEST_GROUP(testPlus);

TEST_SETUP(testPlus)
{
}

TEST_TEAR_DOWN(testPlus)
{
}

TEST(testPlus, plus) {
    TEST_ASSERT(plus(0, 2) == 2);
    TEST_ASSERT(plus(0, -2) == -2);
    TEST_ASSERT(plus(2, 2) == 4);
}

TEST_GROUP_RUNNER(testPlus)
{
    RUN_TEST_CASE(testPlus, plus)
}

