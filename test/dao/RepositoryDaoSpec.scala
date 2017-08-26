package dao

import play.api.test.PlaySpecification
import dao.models.Repository

class RepositoryDaoSpec extends PlaySpecification with Inject{
    lazy val repoDAO = inject[RepositoryDAO]
    "the RepositoryDao" should {
        "create correct repository" in {
            val t_repo = Repository("1", "terrella-api", "1", true, true)
            val result = repoDAO.create(t_repo)

            result must equalTo(true)
        }

        "find correct repository by id" in {
            val result = repoDAO.find("1")
            result.size must_== 1
        }

        "find all repositories." in {
            val result = repoDAO.all
            result must haveSize(be_>(0))
        }
    }
}
