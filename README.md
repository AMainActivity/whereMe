# Приложение «Ты где был?!#»

Приложение для контроля местоположений, имеет следующие основные функциональные возможности:

- периодический контроль местоположений пользователей только в заданное время и дни с минимальной нагрузкой на аккумулятор устройства (потребление 4% заряда за 12 часов работы приложения);
- ожидание получения местоположений с заданной точностью;
- отображение всей истории местоположений на карте в виде маркеров;
- сохранение данных в локальной базе данных устройства при отсутствии подключения к сети и выгрузка на сервер при его появлении;
- возможность поделиться уникальной ссылкой для отслеживания своего местоположения c другими лицами (например отслеживание родителями местоположений детей ) в браузере на любом устройстве. Ссылка вида https://kol.hhos.ru/gkk/map.php?interval=15&wm=uniq_string , содержащая саму уникальную для пользователя строку и интервал обновления данных в секундах;
- Наблюдающий через бот может перейти по ссылке вида https://kol.hhos.ru/gkk/map.php?nid=string2&wm=uniq_string , где может настроить геообласти контроля — при посещении этих областей, наблюдателю придет уведомление от бота;
- регистрация при помощи telegram-бота. Также в боте есть возможность сформировать уникальную ссылку для просмотра своих местоположений; удаление всех своих данных;
- возможность работы в оффлайн-режиме — данные хранятся только на устройстве, а также онлайн — история местоположений хранится на серверной части(их можно просмотреть по уникальной ссылке);
- местоположения на устройстве и на сервере хранятся в измененном (https://kol.hhos.ru/gkk/demo/dmap.php?realmode=false), а их отображение — в корректном виде (https://kol.hhos.ru/gkk/demo/dmap.php?realmode=true).

Подробное описание - https://kol.hhos.ru/2022/12/07/%d0%bf%d1%80%d0%b8%d0%bb%d0%be%d0%b6%d0%b5%d0%bd%d0%b8%d0%b5-%d1%82%d1%8b-%d0%b3%d0%b4%d0%b5-%d0%b1%d1%8b%d0%bb/.

Скачать можно с telegram-бота — https://t.me/myTestInfoBot

//## Внешний вид:

<!--img src="screenshots/1.png" width="250"> <img src="screenshots/2.png" width="250"> <img src="screenshots/3.png" width="250"> <img src="screenshots/4.png" width="250"> <img src="screenshots/5.png" width="250"-->