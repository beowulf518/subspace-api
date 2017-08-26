## Schema definition:

Feel free to tweak!

```
  defaultConnectionArgs = (first: Int, after: String, last: Int, before: String)

  Viewer {
    actor: User {
      id: ID!
      login: String!
      email: String!
      name: String
      photoUrl: String
      repos(defaultConnectionArgs, repoArgs): ReposConnection!
    }

    project(id: ID, name: String, owner: String) Project {
      id: ID!
      name: String!
      repo: Repo!
      owner: User!
      createdAt: DateTime!
      updatedAt: DateTime!
      objectives: [String]
      description: String

      topics(defaultConnectionArgs): TopicsConnection! {
        id: ID!
        value: String!
        createdAt: DataTime!
        projects(defaultConnectionArgs, projectArgs): ProjectsConnection!
      }
    }
    projects(defaultConnectionArgs, projectArgs) ProjectsConnection!

    repo(id: ID, name: String, owner: String): Repo {
      id: ID!
      name: String!
      owner: User!
      project: Project!
      isPrivate: Boolean!
      updatedAt: DateTime!

      ref(qualifiedName: String!) Ref {
        id: ID!
        name: String!r
        repo: Repo!

        target: GitObject! {
          id: ID!
          oid: GitObjectID!
          repo: Repo!
          type: String!

          ... on Commit {

            history(defaultConnectionArgs): CommitsConnection! {
              id: ID!
              message: String!
              author: GitActor!

              tree: GitObject {
                id: ID!
                oid: GitObjectID!
                repo: Repo!

                entries(defaultConnectionArgs): EntriesConnection! {
                  id: ID!
                  oid: GitObjectID!
                  repo: Repo!
                  name: String!
                  type: String!

                  object: GitObject! {
                    ... on Blob {
                      isBinary: Boolean!
                      text: String!
                    }
                  }
                }
              }
          ...
    }

    repos(defaultConnectionArgs, reposArgs) ReposConnection!
  }
```

Also let's use some stuff from this boilerplate: https://github.com/KyleU/boilerplay

Especially for schema declaration:
https://github.com/KyleU/boilerplay/blob/552d52e71ff551b370a35b3655eb3b267dd8b908/app/models/graphql/CommonSchema.scala
