# RedoX Launcher

[English](README.md) · **Türkçe**

Android TV ve Google TV için bir ana ekran. [CleanYourTV](https://github.com/Gathin23/cleanyourtv) projesinin
launcher'ı üzerine kuruldu. Uygulamalar cam bir dock'ta tek sırada duruyor, hava durumu saatin yanında ve ekranın
köşesinde görünüyor, TV'nin yükü tek bakışta okunuyor.

![RedoX Launcher ana ekranı](docs/redox/home.jpg)

## Özellikler

### Cam dock

Tüm uygulamalar, ekranın altında ortalanmış, yuvarlak köşeli cam bir çerçevenin içinde tek sırada duruyor.
Çerçeve dört kart genişliğinde. Kartlar içinde birer birer kayıyor ve altlarında isim yazmıyor; uygulamanın
adı dock'un üstünde görünüyor. Uygulama Kitaplığı düğmesi sıranın sonunda; oradan Sol ya da Yukarı tuşu
uygulamalara geri götürüyor.

Dock'un üstündeki en fazla beş sabit nokta, sırada nerede olduğunuzu gösteriyor.

![Dock'ta gezinme](docs/redox/scroll.jpg)

### Hava durumu

Saatin yanında animasyonlu bir hava simgesi ve sıcaklık var. Hava durumu ayrıca ekranın sol üst köşesinde,
saatin arkasında canlanıyor: yavaşça sallanan güneş ışınları, gece yıldızlar, kayan bulutlar, yağmur, kar,
fırtınada şimşek ya da sis.

| Güneşli | Yağmurlu |
|---|---|
| ![Köşede güneş ışığı](docs/redox/weather-sun.jpg) | ![Köşede yağmur](docs/redox/weather-rain.jpg) |

Veriler, hesap ya da anahtar gerektirmeyen [Open-Meteo](https://open-meteo.com/) servisinden, sizin
yazdığınız `latitude` (enlem) ve `longitude` (boylam) için alınıyor. Ana ekran açıkken 30 dakikada bir
yenileniyor. Konum yazılmadıysa (varsayılan) hava durumu kapalı kalıyor ve hiçbir veri çekilmiyor.

### Renkli istatistikler

Saatin altında CPU, bellek ve depolama doluluğa göre renkleniyor: %50'nin altında yeşil, %50'den itibaren sarı,
%80'den itibaren ya da 1 GB'tan az boş yer kaldığında kırmızı. Her birinin yanında beş çubuklu bir ölçek var.

### Ayrıca

- Seçili uygulamanın rengini alan bir kutup ışıkları (aurora) arka planı.
- Ağ düğmesinde VPN rozeti: VPN açıkken yeşil "VPN Açık", kapalıyken kırmızı "VPN Kapalı". Düğme VPN
  uygulamanızı açıyor.

## Kurulum

TV ile aynı ağda bir bilgisayar gerekiyor: JDK 17, Android SDK (platform 34, build-tools 34.0.0) ve Python 3
kurulu olmalı. TV'de ADB hata ayıklama açık olmalı. ADB'yi açmayı ve eşleştirmeyi adım adım anlatan rehber
orijinal projede (İngilizce): [README.cleanyourtv.md](README.cleanyourtv.md).

1. Bu depoyu klonlayın.
2. `launcher/app/src/main/res/values/config.xml` dosyasını düzenleyin:

   | Ayar | Ne işe yarar |
   |---|---|
   | `dock_apps` | Sıranın başındaki uygulamalar, sırasıyla (paket adları). |
   | `greeting_name` | Saatin üstündeki selamlamadan sonra gelen ad. |
   | `latitude`, `longitude` | Hava durumunun alınacağı konum; örneğin İstanbul için `41.01` ve `28.98`. Boş bırakılırsa hava durumu kapalı kalır. |
   | `vpn_app` | Ağ düğmesinin açtığı VPN uygulaması; örneğin `ch.protonvpn.android`. |

3. Derleyip kurun:

   ```bash
   python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
   TV_HOST=<tv ip> launcher/build-and-install.sh
   ```

   Windows'ta bu komutları Git Bash'te çalıştırın. Betik launcher'ı TV'nin ana ekranı yapar.

4. Kumandada Home tuşuna basın.

İşiniz bitince ADB hata ayıklamayı yeniden kapatın: 5555 numaralı port şifresizdir.

## Bilmeniz gerekenler

- **İnternet erişimi:** Orijinal launcher'ın aksine bunda `INTERNET` izni var ve sadece hava durumu için
  kullanılıyor. Gönderilen tek bilgi, yazdığınız enlem ve boylam.
- **Performans:** Kutup ışıkları ve hava durumu sürekli animasyonlu. 1 GB RAM'li bir TV'de takılmalar olabilir.
- **Paket adı:** Hâlâ `com.gathin.tvhome`. Bu yüzden CleanYourTV launcher'ının üzerine kurulur ve onun
  yerini alır.
- **Denendiği cihaz:** Chromecast with Google TV, Android 14, 1920×1080. Başka TV'lerde yerleşim farklı
  olabilir.
- **Gereksiz uygulamaları kapatma:** `tools/` içindeki araçlar ve `PROMPT.md` içindeki kurallar CleanYourTV'den
  değiştirilmeden geldi. Ayrıntılar için: [README.cleanyourtv.md](README.cleanyourtv.md).

## Emeği geçenler ve lisans

Gathin23 ve katkıda bulunanların [CleanYourTV](https://github.com/Gathin23/cleanyourtv) projesi üzerine kuruldu.
Orijinali gibi MIT lisanslı: [LICENSE](LICENSE).
