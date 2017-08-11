#include <unity_fixture.h>

static void RunAllTests(void)
{
    RUN_TEST_GROUP(testPlus)
    RUN_TEST_GROUP(testMinus);
}

int main(int argc, char* argv[])
{
    UnityMain(argc, argv, RunAllTests);
}
