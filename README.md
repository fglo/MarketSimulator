# MskProject

## Zadanie projektowe z przedmiotu „Metody i techniki symulacji komputerowej

### Treść zadania
W sklepie z kasami losowo przybywający klienci dokonują przez losowy czas zakupów. Po tym wybierają kolejkę i czekają na obsługę. Płacą gotówką. Losowi klienci są uprzywilejowani i podchodzą bez kolejki. Oszacować liczbę kas taką, aby kolejka nie przekroczyła założonej długości.


### Opis federacji

![image](https://user-images.githubusercontent.com/55836292/130470995-61ece557-3eb9-4286-bd48-e8815dad97a7.png)

* sklep – śledzi liczbę i stan klientów w sklepie, określa losowych uprzywilejowanych klientów (losowo, po wejściu do sklepu) 
* klient – robi zakupy, sprawdza, czy skończył szukać produktów w sklepie, idzie do kolejki 
* kolejka – przechowuje listę klientów w kolejce do kasy, wysyła klientów do kasy, sprawdza, czy klient jest uprzywilejowany 
* kasa – obsługuje klientów
* ochroniarz – sprawdza pilnuje warunków narzuconych przez kwarantannę, to znaczy monitoruje ilość klientów w sklepie oraz sprawdza czy klienci posiadają maseczki 

### Model FOM

| **Nazwa federata**   | **Publikuje Interakcje**   | **Publikuje Obiekty**   | **Subskrybuje na Interakcjach**   | **Subskrybuje na Obiektach**   |
| --- | --- | --- | --- | --- |
| Sklep  | OtwarcieSklepu ZamknięcieSklepu OtwarcieKasy ZamkniecieKasy Koniec  |   | UtworzenieKolejki PrzepełnienieKolejki PusteKolejki ZamknięteKasy   | Kolejka   |
| Klienci  | RozpocznijZakupy DolaczDoKolejki BrakKlientów  | klient  | OtwarcieSklepu ZamkniecieSklepu ZakonczObsluge WpuśćKlienta OdmówKlienta OtwórzDrzwi ZamknijDrzwi Koniec   | kolejka  |
| Kolejki  | UtworzenieKolejki WyslijDoKasy PrzepełnienieKolejki PusteKolejiki   | kolejka  | UtworzenieKasy ZamkniecieSklepu RozpocznijObsluge ZakonczObsluge BrakKlientów Koniec  | kasa klient  |
| Kasy  | UtworzenieKasy RozpocznijObsluge ZakonczObsluge ZamknięteKasy  | kasa  | OtwarcieKasy ZamkniecieKasy ZamkniecieSklepu BrakKlientów Koniec  |   |
| Ochroniarz  | WpuśćKlienta OdmówKlienta OtwórzDrzwi ZamknijDrzwi  |   |  Koniec  | klient  |


| **Nazwa obiektu/interakcji** | **Nazwa parametru** | **Typ zawartości** | **Przykładowa wartość** | **Semantyka** |
| --- | --- | --- | --- | --- |
| OtwarcieSklepu | | | | Informacja o otwarciu sklepu |
| ZamknięcieSklepu | | | | Informacja o zamknięciu sklepu |
| OtwarcieKasy | | | | Pozwala na utworzenie obiektu kasy |
| ZamkniecieKasy | Id\_kasy | int | 1 | Pozwala na usunięcie obiektu kasy |
| Koniec | | | | Koniec pracy sklepu |
| klient | Id\_klienta | int | 1 | Pozwala na identyfikacje obiektu klient |
| | Czy\_uprzywilejowany | bool | True/false | Pozwala na określenie pierwszeństwa w ramach kolejki do kasy |
| | Czy\_gotowka | bool | True/false | Pozwala określić czy klient posiada gotówkę |
| RozpocznijZakupy | Id\_klienta | int | 1 | Informuje o tym, że dany klient wszedł do sklepu |
| DołączDoKolejki | Id\_klienta | int | 1 | Informuje o tym, że dany klient zakończył zakupy i ustawia się w kolejce do kasy |
| | Id\_kolejki | int | 1 |
| BrakKlientów | | | | To stan, kiedy ostatni klient zostanie obsłużony i wyjdzie ze sklepu |
| kolejka | Id\_kolejki | int | 1 | Id pozwalające na identyfikację danej kolejki |
| | Id\_kasy | int | 1 | Id pozwalające na przypisanie danej kolejki do danej kasy |
| | Długość | int | 1 | Informacji o tym ile osób aktualnie znajduje się w kolejce |
| UtworzenieKolejki | Id\_kolejki | int | 1 | Utworzenie nowego obiektu kolejki |
| WyślijDoKasy | Id\_klient | int | 1 | Informacja o tym który klient jest wysyłany do obsługi przez kase |
| | Id\_kasy | int | 1 | Informacja o tym która kasa będzie obsługiwać przysyłanego klienta |
| PrzepełnienieKolejki | Id\_kolejki | int | 1 | Informacja o tym, że ilość osób w danej kolejce przekroczyła pewien określony próg |
| PusteKolejki | | | | Informacja, o braku klientów w kolejkach |
| kasa | Id\_kasy | int | 1 | Informacje jednoznacznie identyfikująca obiekt |
| | Id\_klienta | int | 1 | Informacje o tym, który klient jest aktualnie obsługiwany, jeśli kasa zakończyła obsługę Id\_klienta zmieni się na 0, co oznacza, że kolejny klient, może podejść do kasy |
| UtworzenieKasy | id\_kasy | int | 1 | Utworzenie nowego obiektu kasy |
| RozpocznijObsługę | id\_kasy | int | 1 | Identyfikuję która kasa rozpoczęła obsługę (potwierdzenie przejęcia klienta) |
| ZakończObsługę | id\_kasy | int | 1 | Informuje o tym, która kasa zakończyła obsługę |
| | id\_klienta | int | 1 | Informację o zakończeniu zakupów przez danego klienta oraz wyjściu ze sklepu (zniszczenie obiektu klient) |
| ZamknięteKasy | | | | Informacja o zamknięciu kas |
| WpuśćKlienta | Id\_kasy | int | 1 | Informacjo o wpuszczeniu klienta do sklepu |
| OdmówKlienta | Id\_kasy | int | 1 | Informacjo o nie zezwoleniu klientowi na wejście do sklepu |
| OtwórzDrzwi | | | | W momencie kiedy liczba klientów przekracza 50, drzwi do sklepu zostają zamknięte i ochroniarz wpuszcza klientów |
| ZamknijDrzwi | | | | Kiedy w sklepie jest mniej niż 50 osób drzwi do sklepu zostają otwarte |
