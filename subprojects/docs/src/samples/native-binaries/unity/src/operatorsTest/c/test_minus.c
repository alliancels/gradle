#include <unity.h>
#include <unity_fixture.h>
#include "operators.h"

TEST_GROUP(testMinus);

TEST_SETUP(testMinus)
{

}

TEST_TEAR_DOWN(testMinus)
{

}

TEST(testMinus, minus)
{
    TEST_ASSERT(minus(2, 0) == 2);
    TEST_ASSERT(minus(0, -2) == 2);
    TEST_ASSERT(minus(2, 2) == 0);
}

TEST_GROUP_RUNNER(testMinus)
{
    RUN_TEST_CASE(testMinus, minus);
}
