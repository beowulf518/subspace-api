package dao.models

object modelTestData {

    object Repositories {
        val firstRepo = Repository("1", "FirstRepo", "1", true, true)
        val secondRepo = Repository("2", "SecondRepo", "2", true, false)
        val thirdRepo = Repository("3", "ThirdRepo", "3", false, true)
        val fourthRepo = Repository("4", "FourthRepo", "4", false, false)
        val fifthRepo = Repository("5", "FifthRepo", "5", true, true)

        val All = firstRepo :: secondRepo :: thirdRepo :: fourthRepo :: fifthRepo :: Nil
    }

    object Projects {
        val firstProject = Project("1", "FirstProject", "1", "1")
        val secondProject = Project("1", "SecondProject", "1", "2")
        val thirdProject = Project("1", "ThirdProject", "2", "3")
        val fourthProject = Project("1", "FourthProject", "3", "4")
        val fifthProject = Project("1", "FifthProject", "4", "5")

        val All = firstProject :: secondProject :: thirdProject :: fourthProject :: fifthProject :: Nil
    }
}
